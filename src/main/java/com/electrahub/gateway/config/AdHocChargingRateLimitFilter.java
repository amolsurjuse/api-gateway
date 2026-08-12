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
@Order(Ordered.HIGHEST_PRECEDENCE + 19)
public class AdHocChargingRateLimitFilter extends OncePerRequestFilter {
    static final String PATH_PREFIX = "/session/api/v1/sessions/public/ad-hoc/";
    private static final Logger log = LoggerFactory.getLogger(AdHocChargingRateLimitFilter.class);
    private final StringRedisTemplate redis;
    private final boolean enabled;
    private final Duration interval;
    private final String keyPrefix;

    public AdHocChargingRateLimitFilter(
            StringRedisTemplate redis,
            @Value("${app.rate-limit.ad-hoc.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.ad-hoc.requests-per-second:10}") long requestsPerSecond,
            @Value("${app.rate-limit.ad-hoc.key-prefix:rate:afir-ad-hoc:}") String keyPrefix) {
        this.redis = redis;
        this.enabled = enabled;
        this.interval = Duration.ofMillis(Math.max(1, 1000 / Math.max(1, requestsPerSecond)));
        this.keyPrefix = keyPrefix;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !enabled || HttpMethod.OPTIONS.matches(request.getMethod())
                || !request.getRequestURI().startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String key = keyPrefix + hash(clientKey(request));
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", interval))) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader(HttpHeaders.RETRY_AFTER, "1");
                response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"rate_limited\",\"message\":\"Too many ad hoc charging requests.\"}");
                return;
            }
        } catch (RuntimeException exception) {
            log.error("AFIR ad hoc rate-limit check failed; rejecting public request: {}", exception.getMessage());
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, "1");
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"temporarily_unavailable\",\"message\":\"Ad hoc charging is temporarily unavailable.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        String ip = first(request.getHeader("CF-Connecting-IP"));
        if (ip.isBlank()) ip = first(request.getHeader("X-Forwarded-For"));
        if (ip.isBlank()) ip = first(request.getHeader("X-Real-IP"));
        if (ip.isBlank()) ip = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        return ip + "|" + request.getMethod();
    }

    private static String first(String value) {
        if (value == null) return "";
        int comma = value.indexOf(',');
        return (comma < 0 ? value : value.substring(0, comma)).trim().toLowerCase(Locale.ROOT);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
