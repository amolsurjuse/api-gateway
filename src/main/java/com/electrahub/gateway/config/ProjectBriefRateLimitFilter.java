package com.electrahub.gateway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ProjectBriefRateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(ProjectBriefRateLimitFilter.class);

    private final StringRedisTemplate redis;
    private final boolean enabled;
    private final String path;
    private final Duration interval;
    private final String keyPrefix;

    public ProjectBriefRateLimitFilter(
            StringRedisTemplate redis,
            @Value("${app.rate-limit.project-brief.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.project-brief.path:/notifications/api/v1/public/contact-inquiries}") String path,
            @Value("${app.rate-limit.project-brief.requests-per-second:1}") long requestsPerSecond,
            @Value("${app.rate-limit.project-brief.key-prefix:rate:project-brief:}") String keyPrefix
    ) {
        this.redis = redis;
        this.enabled = enabled;
        this.path = path;
        this.interval = Duration.ofMillis(Math.max(1L, 1_000L / Math.max(1L, requestsPerSecond)));
        this.keyPrefix = keyPrefix;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !enabled
                || !HttpMethod.POST.matches(request.getMethod())
                || HttpMethod.OPTIONS.matches(request.getMethod())
                || !path.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String key = keyPrefix + hash(clientKey(request));
            Boolean allowed = redis.opsForValue().setIfAbsent(key, "1", interval);
            if (!Boolean.TRUE.equals(allowed)) {
                reject(response);
                return;
            }
        } catch (RuntimeException ex) {
            log.warn("Project brief rate limit check failed; allowing request", ex);
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, "1");
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"rate_limited\",\"message\":\"Please wait before submitting another project brief.\"}");
    }

    private String clientKey(HttpServletRequest request) {
        String cfConnectingIp = firstHeaderValue(request.getHeader("CF-Connecting-IP"));
        if (!cfConnectingIp.isBlank()) {
            return cfConnectingIp;
        }
        String forwardedFor = firstHeaderValue(request.getHeader("X-Forwarded-For"));
        if (!forwardedFor.isBlank()) {
            return forwardedFor;
        }
        String realIp = firstHeaderValue(request.getHeader("X-Real-IP"));
        if (!realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private String firstHeaderValue(String value) {
        if (value == null) {
            return "";
        }
        int comma = value.indexOf(',');
        return (comma >= 0 ? value.substring(0, comma) : value).trim().toLowerCase(Locale.ROOT);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is unavailable", ex);
        }
    }
}
