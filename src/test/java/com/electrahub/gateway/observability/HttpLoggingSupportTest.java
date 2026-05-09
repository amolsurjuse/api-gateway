package com.electrahub.gateway.observability;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpLoggingSupportTest {

    @Test
    void redactsSensitiveRequestAndResponseHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer secret-token");
        request.addHeader("X-Request-Id", "req-123");
        request.addHeader("X-Internal-Api-Key", "internal-key");

        String requestLog = HttpLoggingSupport.formatRequest(request);
        assertTrue(requestLog.contains("X-Request-Id=[req-123]"));
        assertTrue(requestLog.contains("Authorization=[REDACTED]"));
        assertTrue(requestLog.contains("X-Internal-Api-Key=[REDACTED]"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        response.addHeader("Set-Cookie", "session=abc123");
        response.addHeader("X-Trace-Id", "trace-456");

        String responseLog = HttpLoggingSupport.formatResponse(response);
        assertTrue(responseLog.contains("X-Trace-Id=[trace-456]"));
        assertTrue(responseLog.contains("Set-Cookie=[REDACTED]"));

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Api-Key", "apikey-secret");
        headers.add("Content-Type", "application/json");

        String springHeadersLog = HttpLoggingSupport.formatHeaders(headers);
        assertTrue(springHeadersLog.contains("Content-Type=[application/json]"));
        assertTrue(springHeadersLog.contains("X-Api-Key=[REDACTED]"));
    }
}
