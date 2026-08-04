package com.electrahub.gateway.config;

import com.electrahub.gateway.route.RouteRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Component
public class TermsAcceptanceGateFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TermsAcceptanceGateFilter.class);

    private static final String ACTIVE_VERSION_KEY = "terms:active_version_number";
    private static final String USER_ACCEPTED_PREFIX = "terms:user:";
    private static final Duration ACTIVE_VERSION_TTL = Duration.ofMinutes(5);
    private static final Duration USER_ACCEPTED_TTL = Duration.ofHours(1);

    private final RouteRegistry routeRegistry;
    private final RestClient restClient;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String internalApiKey;

    public TermsAcceptanceGateFilter(
            RouteRegistry routeRegistry,
            RestClient.Builder restClientBuilder,
            StringRedisTemplate redis,
            @Value("${app.rbac.internal-api-key}") String internalApiKey
    ) {
        this.routeRegistry = routeRegistry;
        this.restClient = restClientBuilder.build();
        this.redis = redis;
        this.internalApiKey = internalApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isExcluded(path) || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        Object uid = request.getAttribute("uid");
        if (uid == null) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID userId;
        try {
            userId = UUID.fromString(String.valueOf(uid));
        } catch (IllegalArgumentException ex) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String activeVersion = redis.opsForValue().get(ACTIVE_VERSION_KEY);
            String acceptedVersion = activeVersion == null ? null : redis.opsForValue().get(userAcceptedKey(userId));
            if (activeVersion != null && activeVersion.equals(acceptedVersion)) {
                filterChain.doFilter(request, response);
                return;
            }
        } catch (RuntimeException ex) {
            log.warn("Terms cache unavailable; falling back to terms service path={} uid={} reason={}",
                    path, userId, ex.getMessage());
        }

        TermsGateStatus status = fetchGateStatus(userId);
        if (status == null || status.termsAccepted()) {
            cacheAccepted(userId, status);
            filterChain.doFilter(request, response);
            return;
        }

        writeTermsRequired(response, status);
    }

    private TermsGateStatus fetchGateStatus(UUID userId) {
        String userServiceBaseUrl = routeRegistry.resolve("user");
        if (userServiceBaseUrl == null || userServiceBaseUrl.isBlank()) {
            log.warn("Terms gate skipped because user route is not configured");
            return null;
        }
        try {
            return restClient.get()
                    .uri(URI.create(userServiceBaseUrl + "/api/internal/terms/gate-status?userId=" + userId))
                    .header("X-Internal-Api-Key", internalApiKey)
                    .retrieve()
                    .body(TermsGateStatus.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                log.warn("Terms gate skipped because no active Terms version is configured");
                return null;
            }
            log.warn("Terms gate check failed status={} uid={} body={}",
                    ex.getStatusCode().value(), userId, ex.getResponseBodyAsString());
            return null;
        } catch (RuntimeException ex) {
            log.warn("Terms gate check failed uid={} reason={}", userId, ex.getMessage());
            return null;
        }
    }

    private void cacheAccepted(UUID userId, TermsGateStatus status) {
        if (status == null) {
            return;
        }
        try {
            String version = String.valueOf(status.currentVersionNumber());
            redis.opsForValue().set(ACTIVE_VERSION_KEY, version, ACTIVE_VERSION_TTL);
            if (status.termsAccepted()) {
                redis.opsForValue().set(userAcceptedKey(userId), version, USER_ACCEPTED_TTL);
            }
        } catch (RuntimeException ex) {
            log.debug("Unable to write Terms gate cache uid={} reason={}", userId, ex.getMessage());
        }
    }

    private void writeTermsRequired(HttpServletResponse response, TermsGateStatus status) throws IOException {
        response.setStatus(HttpStatus.UNAVAILABLE_FOR_LEGAL_REASONS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        Map<String, Object> body = Map.of(
                "error", "TERMS_ACCEPTANCE_REQUIRED",
                "currentVersionNumber", status.currentVersionNumber(),
                "currentVersionLabel", status.currentVersionLabel(),
                "contentUrl", status.contentUrl(),
                "contentSha256", status.contentSha256()
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private boolean isExcluded(String path) {
        return path == null
                || path.startsWith("/terms/api/v1/terms/")
                || path.startsWith("/user/api/v1/terms/")
                || path.startsWith("/auth/api/terms/")
                || path.startsWith("/admin/api/v1/terms")
                || path.equals("/auth/api/auth/login")
                || path.equals("/auth/api/auth/register")
                || path.equals("/auth/api/auth/refresh")
                || path.startsWith("/auth/api/auth/logout")
                || path.startsWith("/payment-gateway/api/v1/gateway/webhooks/")
                || path.equals("/session/api/v1/sessions/active/stream")
                || path.startsWith("/actuator/")
                || path.startsWith("/v3/api-docs/")
                || path.startsWith("/swagger-ui")
                || path.equals("/swagger-ui.html")
                || path.startsWith("/internal/api/");
    }

    private String userAcceptedKey(UUID userId) {
        return USER_ACCEPTED_PREFIX + userId + ":accepted_version";
    }

    private record TermsGateStatus(
            boolean termsAccepted,
            int currentVersionNumber,
            Integer acceptedVersionNumber,
            String currentVersionLabel,
            String contentUrl,
            String contentSha256
    ) {
    }
}
