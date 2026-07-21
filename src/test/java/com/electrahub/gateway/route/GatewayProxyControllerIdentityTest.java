package com.electrahub.gateway.route;

import com.electrahub.gateway.config.HttpClientConfig.GatewayHttpClientProperties;
import com.electrahub.gateway.security.GatewayAccessScope;
import com.electrahub.gateway.security.GatewayAccessScopeHeaderSigner;
import com.electrahub.gateway.security.GatewayAccessScopeResolver;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

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

    @Test
    void requiresSignedScopeForBothChargerAdministrationAliases() throws Exception {
        GatewayProxyController controller = controller();

        assertThat(requiresScopedAdministrativeAccess(controller, "/charger/api/v1/admin/chargers")).isTrue();
        assertThat(requiresScopedAdministrativeAccess(controller, "/charger-management/api/v1/admin/chargers")).isTrue();
        assertThat(requiresScopedAdministrativeAccess(controller, "/payment-gateway/api/v1/gateway/admin/connections")).isTrue();
        assertThat(requiresScopedAdministrativeAccess(controller, "/charger/graphql")).isFalse();
        assertThat(requiresScopedAdministrativeAccess(controller, "/payment-gateway/api/v1/gateway/internal/routes/resolve")).isFalse();
    }

    @Test
    void doesNotForwardBearerTokenForScopedAdministrativeRequests() throws Exception {
        GatewayProxyController controller = controller();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("uid", "trusted-user");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer client-token");
        HttpHeaders downstream = new HttpHeaders();

        copyHeaders(controller, request, downstream, new GatewayAccessScope(
                UUID.randomUUID(),
                false,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Instant.now().plusSeconds(30)
        ));

        assertThat(downstream.getFirst(HttpHeaders.AUTHORIZATION)).isNull();
        assertThat(downstream.getFirst("X-ElectraHub-User-Id")).isEqualTo("trusted-user");
    }

    private GatewayProxyController controller() {
        return new GatewayProxyController(
                new RouteRegistry(),
                RestClient.builder(),
                mock(HttpExchangeLogger.class),
                new GatewayHttpClientProperties(Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1)),
                mock(GatewayAccessScopeResolver.class),
                mock(GatewayAccessScopeHeaderSigner.class),
                "electrahub"
        );
    }

    private void copyHeaders(
            GatewayProxyController controller,
            MockHttpServletRequest request,
            HttpHeaders downstream
    ) throws Exception {
        copyHeaders(controller, request, downstream, null);
    }

    private void copyHeaders(
            GatewayProxyController controller,
            MockHttpServletRequest request,
            HttpHeaders downstream,
            GatewayAccessScope scope
    ) throws Exception {
        Method method = GatewayProxyController.class.getDeclaredMethod(
                "copyHeaders",
                jakarta.servlet.http.HttpServletRequest.class,
                HttpHeaders.class,
                GatewayAccessScope.class
        );
        method.setAccessible(true);
        method.invoke(controller, request, downstream, scope);
    }

    private boolean requiresScopedAdministrativeAccess(
            GatewayProxyController controller,
            String path
    ) throws Exception {
        Method resolveRouteTarget = GatewayProxyController.class.getDeclaredMethod("resolveRouteTarget", String.class);
        resolveRouteTarget.setAccessible(true);
        Object routeTarget = resolveRouteTarget.invoke(controller, path);

        Method requiresScope = GatewayProxyController.class.getDeclaredMethod(
                "requiresScopedAdministrativeAccess",
                routeTarget.getClass()
        );
        requiresScope.setAccessible(true);
        return (boolean) requiresScope.invoke(controller, routeTarget);
    }
}
