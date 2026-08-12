package com.electrahub.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdHocChargingRateLimitFilterTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    @Test
    void allowsFirstPublicPriceRequest() throws Exception {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(startsWith("rate:afir:"), any(), any(Duration.class))).thenReturn(true);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter().doFilter(request(), response, chain);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void rejectsBurstWithoutLeakingDetails() throws Exception {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(startsWith("rate:afir:"), any(), any(Duration.class))).thenReturn(false);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter().doFilter(request(), response, chain);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void skipsAuthenticatedSessionRoutes() throws Exception {
        var request = new MockHttpServletRequest("GET", "/session/api/v1/sessions/history");
        var chain = new MockFilterChain();
        filter().doFilter(request, new MockHttpServletResponse(), chain);
        verify(redis, never()).opsForValue();
    }

    @Test
    void failsClosedWhenRedisCannotEnforceThePublicLimit() throws Exception {
        when(redis.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter().doFilter(request(), response, chain);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(chain.getRequest()).isNull();
    }

    private AdHocChargingRateLimitFilter filter() {
        return new AdHocChargingRateLimitFilter(redis, true, 10, "rate:afir:");
    }

    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("POST", AdHocChargingRateLimitFilter.PATH_PREFIX + "price-disclosures");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.2");
        return request;
    }
}
