package com.electrahub.gateway.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TraceContextMdcFilterTest {

    @Test
    void createsRequestSpanAndSeedsTraceIds() throws Exception {
        Tracer tracer = mock(Tracer.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<Tracer> tracerProvider = mock(ObjectProvider.class);
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        Tracer.SpanInScope scope = mock(Tracer.SpanInScope.class);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/rbac/cache/invalidate");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceId = new AtomicReference<>();
        AtomicReference<String> spanId = new AtomicReference<>();

        when(tracer.currentSpan()).thenReturn(null);
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name("GET /internal/rbac/cache/invalidate")).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("trace-123");
        when(context.spanId()).thenReturn("span-456");
        when(tracerProvider.getIfAvailable()).thenReturn(tracer);
        when(tracer.withSpan(span)).thenReturn(scope);

        TraceContextMdcFilter filter = new TraceContextMdcFilter(tracerProvider) {
            @Override
            protected void doFilterInternal(@NonNull HttpServletRequest req, @NonNull HttpServletResponse res, @NonNull FilterChain chain)
                    throws IOException, jakarta.servlet.ServletException {
                super.doFilterInternal(req, res, (r, s) -> {
                    traceId.set(org.slf4j.MDC.get("traceId"));
                    spanId.set(org.slf4j.MDC.get("spanId"));
                    chain.doFilter(r, s);
                });
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(traceId.get()).isEqualTo("trace-123");
        assertThat(spanId.get()).isEqualTo("span-456");
        verify(span).end();
        verify(chain).doFilter(request, response);
        verify(scope).close();
    }
}

