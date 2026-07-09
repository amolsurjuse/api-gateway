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

class ProjectBriefRateLimitFilterTest {
    private static final String CONTACT_PATH = "/notifications/api/v1/public/contact-inquiries";

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    @Test
    void allowsFirstProjectBriefRequestInWindow() throws Exception {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(startsWith("rate:project-brief:"), any(), any(Duration.class))).thenReturn(true);

        ProjectBriefRateLimitFilter filter = filter();
        MockHttpServletRequest request = projectBriefRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_OK);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void rejectsSecondProjectBriefRequestInWindow() throws Exception {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(startsWith("rate:project-brief:"), any(), any(Duration.class))).thenReturn(false);

        ProjectBriefRateLimitFilter filter = filter();
        MockHttpServletRequest request = projectBriefRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("1");
        assertThat(response.getContentAsString()).contains("rate_limited");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void skipsOtherPaths() throws Exception {
        ProjectBriefRateLimitFilter filter = filter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/notifications/api/v1/inbox");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        verify(redis, never()).opsForValue();
    }

    private ProjectBriefRateLimitFilter filter() {
        return new ProjectBriefRateLimitFilter(redis, true, CONTACT_PATH, 1L, "rate:project-brief:");
    }

    private MockHttpServletRequest projectBriefRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", CONTACT_PATH);
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
        request.setRemoteAddr("10.0.0.20");
        return request;
    }
}
