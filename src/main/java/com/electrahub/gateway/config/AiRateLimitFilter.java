package com.electrahub.gateway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/** Limits authenticated AI calls before they consume downstream model capacity. */
@Component
public class AiRateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AiRateLimitFilter.class);

    private final StringRedisTemplate redis;
    private final boolean enabled;
    private final String pathPrefix;
    private final Duration interval;
    private final String keyPrefix;

    public AiRateLimitFilter(
            StringRedisTemplate redis,
            @Value("${app.rate-limit.ai.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.ai.path-prefix:/ai/}") String pathPrefix,
            @Value("${app.rate-limit.ai.requests-per-second:2}") long requestsPerSecond,
            @Value("${app.rate-limit.ai.key-prefix:rate:ai:}") String keyPrefix
    ) {
        this.redis = redis;
        this.enabled = enabled;
        this.pathPrefix = pathPrefix.endsWith("/") ? pathPrefix : pathPrefix + "/";
        this.interval = Duration.ofMillis(Math.max(1L, 1_000L / Math.max(1L, requestsPerSecond)));
        this.keyPrefix = keyPrefix;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || HttpMethod.OPTIONS.matches(request.getMethod())
                || !request.getRequestURI().startsWith(pathPrefix);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String tenant = value(request, "tenantId", "default");
            String user = value(request, "uid", request.getRemoteUser() == null ? "anonymous" : request.getRemoteUser());
            Boolean allowed = redis.opsForValue().setIfAbsent(keyPrefix + tenant + ":" + user, "1", interval);
            if (!Boolean.TRUE.equals(allowed)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader(HttpHeaders.RETRY_AFTER, "1");
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"rate_limited\",\"message\":\"AI request limit is 2 requests per second.\"}");
                return;
            }
        } catch (RuntimeException ex) {
            log.warn("AI rate limit check failed; allowing request", ex);
        }
        filterChain.doFilter(request, response);
    }

    private String value(HttpServletRequest request, String attribute, String fallback) {
        Object value = request.getAttribute(attribute);
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }
}
