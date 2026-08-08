package com.electrahub.gateway.security;

import com.electrahub.gateway.route.RouteRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayAccessScopeResolverTest {

    @Test
    void givesGlobalReadScopeToReadOnlyAdminWithoutLocationExpansion() {
        UUID actorId = UUID.randomUUID();
        RouteRegistry routeRegistry = new RouteRegistry();
        routeRegistry.setRoutes(Map.of(
                "user", "http://user-service",
                "charger-management", "http://charger-service"
        ));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GatewayAccessScopeCache scopeCache = mock(GatewayAccessScopeCache.class);
        when(scopeCache.lookup(eq(actorId), eq("1"), eq("readonly-token")))
                .thenReturn(GatewayAccessScopeCache.Lookup.miss("0", "0:" + "r".repeat(43)));
        when(scopeCache.store(eq(actorId), eq("1"), eq("readonly-token"), eq("0"), any(GatewayAccessScope.class)))
                .thenReturn(GatewayAccessScopeCache.StoreResult.STORED);
        GatewayAccessScopeResolver resolver = new GatewayAccessScopeResolver(
                routeRegistry,
                builder,
                new GatewayAccessScopeHeaderSigner(new ObjectMapper(), "test-access-context-secret", "test"),
                scopeCache,
                Duration.ofSeconds(30)
        );

        server.expect(once(), requestTo("http://user-service/api/v1/admin/access/me"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"actorId":"%s","systemAdmin":false,"grants":[]}
                        """.formatted(actorId), MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("uid", actorId.toString());
        request.setAttribute("tv", "1");
        request.setAttribute("jti", "readonly-token");
        request.setAttribute("roles", java.util.List.of("USER", "ADMIN_READ_ONLY"));
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer readonly-token");

        GatewayAccessScope scope = resolver.resolve(request);

        assertThat(scope.systemAdmin()).isTrue();
        assertThat(scope.operateEnterpriseIds()).isEmpty();
        assertThat(scope.operateNetworkIds()).isEmpty();
        assertThat(scope.operateLocationIds()).isEmpty();
        server.verify();
    }

    @Test
    void signsAnEmptyScopeForAnAdminWithoutGrants() {
        UUID actorId = UUID.randomUUID();
        RouteRegistry routeRegistry = new RouteRegistry();
        routeRegistry.setRoutes(Map.of(
                "user", "http://user-service",
                "charger-management", "http://charger-service"
        ));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GatewayAccessScopeHeaderSigner signer = new GatewayAccessScopeHeaderSigner(
                new ObjectMapper(),
                "test-access-context-secret",
                "test"
        );
        GatewayAccessScopeCache scopeCache = mock(GatewayAccessScopeCache.class);
        when(scopeCache.lookup(eq(actorId), eq("1"), eq("test-token")))
                .thenReturn(GatewayAccessScopeCache.Lookup.miss("0", "0:" + "a".repeat(43)));
        when(scopeCache.store(eq(actorId), eq("1"), eq("test-token"), eq("0"), any(GatewayAccessScope.class)))
                .thenReturn(GatewayAccessScopeCache.StoreResult.STORED);
        GatewayAccessScopeResolver resolver = new GatewayAccessScopeResolver(
                routeRegistry,
                builder,
                signer,
                scopeCache,
                Duration.ofSeconds(30)
        );

        server.expect(once(), requestTo("http://user-service/api/v1/admin/access/me"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"actorId":"%s","systemAdmin":false,"grants":[]}
                        """.formatted(actorId), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://charger-service/api/v1/internal/access/expand-locations"))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {"readLocationIds":[],"operateLocationIds":[]}
                        """, MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("uid", actorId.toString());
        request.setAttribute("tv", "1");
        request.setAttribute("jti", "test-token");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token");

        GatewayAccessScope scope = resolver.resolve(request);

        assertThat(scope.systemAdmin()).isFalse();
        assertThat(scope.readLocationIds()).isEmpty();
        assertThat(scope.operateLocationIds()).isEmpty();
        verify(scopeCache).store(eq(actorId), eq("1"), eq("test-token"), eq("0"), any(GatewayAccessScope.class));
        server.verify();
    }

    @Test
    void reusesRedisScopeWithoutCallingUserOrChargerServices() {
        UUID actorId = UUID.randomUUID();
        GatewayAccessScopeCache scopeCache = mock(GatewayAccessScopeCache.class);
        GatewayAccessScope cached = new GatewayAccessScope(
                actorId,
                false,
                java.util.Set.of(),
                java.util.Set.of("NETWORK-1"),
                java.util.Set.of("LOCATION-1"),
                java.util.Set.of(),
                java.util.Set.of(),
                java.util.Set.of(),
                Instant.EPOCH
        );
        when(scopeCache.lookup(eq(actorId), eq("4"), eq("token-4")))
                .thenReturn(GatewayAccessScopeCache.Lookup.hit("3", "3:" + "b".repeat(43), cached));

        GatewayAccessScopeResolver resolver = new GatewayAccessScopeResolver(
                new RouteRegistry(),
                RestClient.builder(),
                new GatewayAccessScopeHeaderSigner(new ObjectMapper(), "test-access-context-secret", "test"),
                scopeCache,
                Duration.ofSeconds(30)
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("uid", actorId.toString());
        request.setAttribute("tv", "4");
        request.setAttribute("jti", "token-4");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-4");

        GatewayAccessScope scope = resolver.resolve(request);

        assertThat(scope.readLocationIds()).containsExactly("LOCATION-1");
        assertThat(scope.expiresAt()).isAfter(Instant.now());
        assertThat(scope.scopeReference()).isEqualTo("3:" + "b".repeat(43));
    }
}
