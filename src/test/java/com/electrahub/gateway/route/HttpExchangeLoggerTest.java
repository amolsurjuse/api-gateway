package com.electrahub.gateway.route;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class HttpExchangeLoggerTest {

    @Test
    void masksSensitiveHeadersQueryAndJsonBody(CapturedOutput output) {
        HttpExchangeLogger logger = new HttpExchangeLogger(true, true, true, 4096);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/api/auth/login");
        request.setQueryString("email=user@example.com&access_token=query-secret");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer header-secret");
        request.addHeader(HttpHeaders.COOKIE, "session=cookie-secret");
        request.setContentType("application/json");

        byte[] requestBody = """
                {"email":"user@example.com","password":"body-secret","accessToken":"access-secret"}
                """.getBytes(StandardCharsets.UTF_8);

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add(HttpHeaders.SET_COOKIE, "refresh=refresh-secret");
        byte[] responseBody = """
                {"accessToken":"response-secret","refreshToken":"refresh-secret"}
                """.getBytes(StandardCharsets.UTF_8);

        long startedAtNanos = logger.started();
        logger.logRequest(request, HttpMethod.POST, "/auth/api/auth/login",
                "http://auth-service:8080/api/auth/login?access_token=downstream-secret", requestBody);
        logger.logResponse(request, HttpMethod.POST, "/auth/api/auth/login",
                "http://auth-service:8080/api/auth/login?access_token=downstream-secret",
                200, responseHeaders, responseBody, startedAtNanos);

        assertThat(output).contains("GW_REQUEST", "GW_RESPONSE", "user@example.com");
        assertThat(output).contains("access_token=***");
        assertThat(output).contains("\"password\":\"***\"");
        assertThat(output).contains("\"accessToken\":\"***\"");
        assertThat(output).contains("\"refreshToken\":\"***\"");
        assertThat(output).doesNotContain(
                "query-secret",
                "header-secret",
                "cookie-secret",
                "body-secret",
                "access-secret",
                "response-secret",
                "refresh-secret",
                "downstream-secret"
        );
    }

    @Test
    void omitsBinaryBodies(CapturedOutput output) {
        HttpExchangeLogger logger = new HttpExchangeLogger(true, true, true, 4096);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/files/upload");
        request.setContentType("application/octet-stream");

        logger.logRequest(request, HttpMethod.POST, "/files/upload",
                "http://file-service:8080/upload", new byte[]{0x01, 0x02, 0x03});

        assertThat(output).contains("<omitted contentType=application/octet-stream bytes=3>");
    }

    @Test
    void logsFullTextBodyWhenFullRequestResponseFlagEnabled(CapturedOutput output) {
        HttpExchangeLogger logger = new HttpExchangeLogger(true, false, false, true, 5);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/api/auth/login");
        request.setContentType("application/json");
        request.addHeader("X-Test", "header-value");

        byte[] requestBody = "{\"message\":\"1234567890\"}".getBytes(StandardCharsets.UTF_8);

        logger.logRequest(request, HttpMethod.POST, "/auth/api/auth/login",
                "http://auth-service:8080/api/auth/login", requestBody);

        assertThat(output).contains("header-value");
        assertThat(output).contains("1234567890");
        assertThat(output).doesNotContain("<disabled>");
        assertThat(output).doesNotContain("<truncated");
    }

    @Test
    void omitsProviderWebhookBodyAndSignatureHeaders(CapturedOutput output) {
        HttpExchangeLogger logger = new HttpExchangeLogger(true, true, true, 4096);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/payment-gateway/api/v1/gateway/webhooks/00000000-0000-0000-0000-000000000001"
        );
        request.setContentType("application/json");
        request.addHeader("Stripe-Signature", "t=123,v1=provider-secret");
        byte[] body = "{\"payload\":\"signed-payment-payload\"}".getBytes(StandardCharsets.UTF_8);

        logger.logRequest(
                request,
                HttpMethod.POST,
                request.getRequestURI(),
                "http://payment-gateway-service:8098/api/v1/gateway/webhooks/id",
                body
        );

        assertThat(output).contains("<verified-provider-webhook-omitted>", "Stripe-Signature=[***]");
        assertThat(output).doesNotContain("provider-secret", "signed-payment-payload");
    }
}
