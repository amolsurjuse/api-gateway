package com.electrahub.gateway.security;

import com.electrahub.gateway.config.RbacProperties;
import com.electrahub.gateway.route.RouteRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SupportSessionAccessTest {
    @Test
    void shippedPolicyAllowsScopedReadsAndChatButNeverSupportOperationalWrites() throws Exception {
        var environment = new StandardEnvironment();
        for (var source : new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml")))
            environment.getPropertySources().addLast(source);
        var policy = RbacPolicySnapshot.fromProperties(Binder.get(environment).bind("app.rbac", RbacProperties.class).get());
        var provider = mock(RbacPolicySnapshotProvider.class);
        when(provider.currentPolicy()).thenReturn(policy);
        var manager = new ApiPolicyAuthorizationManager(provider);
        assertThat(allowed(manager, "GET", "/session/api/v1/sessions/admin/123/diagnostics", "SUPPORT")).isTrue();
        assertThat(allowed(manager, "GET", "/session/api/v1/sessions/admin/123/diagnostics", "SYSTEM_ADMIN")).isTrue();
        for (String role : Set.of("ADMIN_READ_ONLY", "TENANT_ADMIN", "ENTERPRISE", "NETWORK", "LOCATION", "USER")) {
            assertThat(allowed(manager, "GET", "/session/api/v1/sessions/admin/123/diagnostics", role)).as(role).isFalse();
        }
        assertThat(allowed(manager, "GET", "/session/api/v1/sessions/admin", "SUPPORT")).isTrue();
        assertThat(allowed(manager, "GET", "/charger/api/v1/admin/locations", "SUPPORT")).isTrue();
        assertThat(allowed(manager, "POST", "/ai/api/v1/chat/messages", "SUPPORT")).isTrue();
        assertThat(allowed(manager, "POST", "/ai/api/v1/chat/messages", "SUPPORT", "ADMIN_READ_ONLY")).isTrue();
        assertThat(allowed(manager, "GET", "/session/api/v1/sessions/admin/123/diagnostics", "SUPPORT", "ADMIN_READ_ONLY")).isTrue();
        assertThat(allowed(manager, "GET", "/ai/api/v1/chat/threads/123/stream", "SUPPORT")).isTrue();
        assertThat(allowed(manager, "GET", "/ai/api/v1/chat/threads/123/stream")).isFalse();
        assertThat(allowed(manager, "POST", "/ai/api/v1/chat/threads/123/stream", "SUPPORT")).isFalse();
        assertThat(allowed(manager, "POST", "/user/api/v1/terms/accept", "SUPPORT")).isTrue();
        assertThat(allowed(manager, "POST", "/session/api/v1/sessions/admin/123/stop", "SUPPORT", "LOCATION", "USER")).isFalse();
        assertThat(allowed(manager, "POST", "/payment/api/v1/admin/refunds", "SUPPORT", "USER")).isFalse();
        assertThat(allowed(manager, "GET", "/user/api/v1/admin/users", "SUPPORT")).isFalse();
        assertThat(allowed(manager, "POST", "/ai/api/v1/admin/approvals/123/execute", "SUPPORT")).isFalse();
    }

    @Test
    void supportScopeIsDerivedFromAssignedGrantsWithoutGlobalAccess() {
        UUID actor = UUID.randomUUID();
        var routes = new RouteRegistry();
        routes.setRoutes(Map.of("user", "https://users.test", "charger-management", "https://chargers.test"));
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://users.test/api/v1/admin/access/me"))
                .andRespond(withSuccess("{\"actorId\":\"" + actor + "\",\"systemAdmin\":false,\"grants\":[{\"scopeType\":\"LOCATION\",\"scopeId\":\"LOC-A\",\"accessLevel\":\"READ\"}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://chargers.test/api/v1/internal/access/expand-locations"))
                .andRespond(withSuccess("{\"readLocationIds\":[\"LOC-A\"],\"operateLocationIds\":[]}", MediaType.APPLICATION_JSON));
        var resolver = new GatewayAccessScopeResolver(routes, builder,
                new GatewayAccessScopeHeaderSigner(new ObjectMapper(), "test-secret", "test"),
                mock(GatewayAccessScopeCache.class), Duration.ofSeconds(30));
        var request = new MockHttpServletRequest();
        request.setAttribute("uid", actor.toString());
        request.setAttribute("roles", List.of("SUPPORT"));
        request.addHeader("Authorization", "Bearer support-token");
        var scope = resolver.resolve(request);
        assertThat(scope.systemAdmin()).isFalse();
        assertThat(scope.readLocationIds()).containsExactly("LOC-A");
        assertThat(scope.operateLocationIds()).isEmpty();
        server.verify();
    }

    private boolean allowed(ApiPolicyAuthorizationManager manager, String method, String path, String... roles) {
        var authentication = new UsernamePasswordAuthenticationToken("support", "unused", Arrays.stream(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList());
        return manager.authorize(() -> authentication, new RequestAuthorizationContext(new MockHttpServletRequest(method, path))).isGranted();
    }
}
