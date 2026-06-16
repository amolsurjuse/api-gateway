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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Component
@NullMarked
public class TraceContextMdcFilter extends OncePerRequestFilter {

    private static final String TRACE_ID = "traceId";
    private static final String SPAN_ID = "spanId";

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
            traceId = generateTraceId();
            spanId = generateSpanId();
        }

        Map<String, String> previous = MDC.getCopyOfContextMap();
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
}

