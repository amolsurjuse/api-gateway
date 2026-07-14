package com.electrahub.gateway.route;

import com.electrahub.gateway.config.HttpClientConfig.GatewayHttpClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Method;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GatewayProxyControllerIdentityTest {
    @Test
    void replacesClientIdentityHeadersWithValidatedJwtIdentity() throws Exception {
        GatewayProxyController controller = controller();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-ElectraHub-User-Id", "spoofed-user");
        request.addHeader("X-ElectraHub-Tenant-Id", "spoofed-tenant");
        request.addHeader("X-Client-Version", "1.0");
        request.setAttribute("uid", "trusted-user");
        HttpHeaders downstream = new HttpHeaders();

        copyHeaders(controller, request, downstream);

        assertThat(downstream.getFirst("X-ElectraHub-User-Id")).isEqualTo("trusted-user");
        assertThat(downstream.getFirst("X-ElectraHub-Tenant-Id")).isEqualTo("electrahub");
        assertThat(downstream.getFirst("X-Client-Version")).isEqualTo("1.0");
    }

    @Test
    void removesClientIdentityHeadersWhenRequestIsAnonymous() throws Exception {
        GatewayProxyController controller = controller();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-ElectraHub-User-Id", "spoofed-user");
        request.addHeader("X-ElectraHub-Tenant-Id", "spoofed-tenant");
        HttpHeaders downstream = new HttpHeaders();

        copyHeaders(controller, request, downstream);

        assertThat(downstream.getFirst("X-ElectraHub-User-Id")).isNull();
        assertThat(downstream.getFirst("X-ElectraHub-Tenant-Id")).isNull();
    }

    private GatewayProxyController controller() {
        return new GatewayProxyController(
                new RouteRegistry(),
                RestClient.builder(),
                mock(HttpExchangeLogger.class),
                new GatewayHttpClientProperties(Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1)),
                "electrahub"
        );
    }

    private void copyHeaders(
            GatewayProxyController controller,
            MockHttpServletRequest request,
            HttpHeaders downstream
    ) throws Exception {
        Method method = GatewayProxyController.class.getDeclaredMethod(
                "copyHeaders",
                jakarta.servlet.http.HttpServletRequest.class,
                HttpHeaders.class
        );
        method.setAccessible(true);
        method.invoke(controller, request, downstream);
    }
}
