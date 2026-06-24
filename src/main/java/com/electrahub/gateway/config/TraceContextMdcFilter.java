package com.electrahub.gateway.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@NullMarked
public class TraceContextMdcFilter extends OncePerRequestFilter {

    private static final String TRACE_ID = "traceId";
    private static final String SPAN_ID = "spanId";
    private static final String TRACEPARENT = "traceparent";
    private static final String X_TRACE_ID = "X-Trace-Id";
    private static final String X_SPAN_ID = "X-Span-Id";
    private static final String B3_TRACE_ID = "X-B3-TraceId";
    private static final String B3_SPAN_ID = "X-B3-SpanId";

    private final ObjectProvider<Tracer> tracerProvider;

    public TraceContextMdcFilter(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Tracer tracer = tracerProvider.getIfAvailable();
        Span span = tracer == null ? null : tracer.currentSpan();
        boolean createdSpan = false;
        if (tracer != null && (span == null || span.isNoop())) {
            span = tracer.nextSpan().name(request.getMethod() + " " + request.getRequestURI()).start();
            createdSpan = true;
        }

        String traceId;
        String spanId;
        if (span != null && !span.isNoop()) {
            TraceContext context = span.context();
            traceId = context.traceId();
            spanId = context.spanId();
        } else {
            traceId = firstNonBlank(extractTraceparentTraceId(request.getHeader(TRACEPARENT)),
                    request.getHeader(X_TRACE_ID), request.getHeader(B3_TRACE_ID), generateTraceId());
            spanId = firstNonBlank(extractTraceparentSpanId(request.getHeader(TRACEPARENT)),
                    request.getHeader(X_SPAN_ID), request.getHeader(B3_SPAN_ID), generateSpanId());
        }

        Map<String, String> previous = MDC.getCopyOfContextMap();
        response.setHeader(X_TRACE_ID, traceId);
        response.setHeader(X_SPAN_ID, spanId);
        if (isHex(traceId, 32) && isHex(spanId, 16)) {
            response.setHeader(TRACEPARENT, "00-" + traceId + "-" + spanId + "-01");
        }
        if (tracer != null && span != null && !span.isNoop()) {
            try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
                MDC.put(TRACE_ID, traceId);
                MDC.put(SPAN_ID, spanId);
                filterChain.doFilter(request, response);
            } finally {
                if (previous == null || previous.isEmpty()) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previous);
                }
                if (createdSpan) {
                    span.end();
                }
            }
            return;
        }

        try {
            MDC.put(TRACE_ID, traceId);
            MDC.put(SPAN_ID, spanId);
            filterChain.doFilter(request, response);
        } finally {
            if (previous == null || previous.isEmpty()) {
                MDC.clear();
            } else {
                MDC.setContextMap(previous);
            }
            if (createdSpan) {
                span.end();
            }
        }
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return generateTraceId();
    }

    private String extractTraceparentTraceId(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) return null;
        String[] parts = traceparent.split("-");
        if (parts.length >= 4 && isHex(parts[1], 32)) return parts[1];
        return null;
    }

    private String extractTraceparentSpanId(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) return null;
        String[] parts = traceparent.split("-");
        if (parts.length >= 4 && isHex(parts[2], 16)) return parts[2];
        return null;
    }

    private boolean isHex(String value, int length) {
        return value != null && value.length() == length && value.matches("[0-9a-fA-F]+");
    }
}

