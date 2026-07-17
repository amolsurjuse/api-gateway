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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

class GatewayAccessScopeResolverTest {

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
        GatewayAccessScopeResolver resolver = new GatewayAccessScopeResolver(
                routeRegistry,
                builder,
                signer,
                Duration.ofSeconds(15),
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
        server.verify();
    }
}
