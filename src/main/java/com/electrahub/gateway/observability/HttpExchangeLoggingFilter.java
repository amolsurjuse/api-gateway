package com.electrahub.gateway.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpExchangeLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpExchangeLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startedAt = System.nanoTime();
        String method = request.getMethod();
        String path = request.getRequestURI();
        String query = request.getQueryString();
        String requestLine = query == null || query.isBlank() ? path : path + "?" + query;

        log.info("HTTP request: method={} path={}", method, requestLine);
        if (log.isDebugEnabled()) {
            log.debug("HTTP request headers: {}", HttpLoggingSupport.formatRequest(request));
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            log.info("HTTP response: method={} path={} status={} durationMs={}",
                    method, requestLine, response.getStatus(), durationMs);
            if (log.isDebugEnabled()) {
                log.debug("HTTP response headers: {}", HttpLoggingSupport.formatResponse(response));
            }
        }
    }
}
