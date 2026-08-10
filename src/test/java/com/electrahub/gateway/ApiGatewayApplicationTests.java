package com.electrahub.gateway;

import com.electrahub.gateway.config.RbacProperties;
import com.electrahub.gateway.route.RouteRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "app.security.jwt.secret=test-secret-key-that-is-at-least-64-characters-long-for-hmac-sha-algorithm-testing",
        "app.security.jwt.issuer=auth-service",
        "app.redis.denylist-prefix=deny:jwt:",
        "app.redis.token-version-prefix=tv:",
        "gateway.routes.auth=http://localhost:8080",
        "gateway.routes.user=http://localhost:8082",
        "gateway.routes.ocpi=http://127.0.0.1:1/ocpi",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"
})
class ApiGatewayApplicationTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiGatewayApplicationTests.class);

    @Autowired
    private RbacProperties rbacProperties;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RouteRegistry routeRegistry;


    /**
     * Executes context loads for `ApiGatewayApplicationTests`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway`.
     */
    @Test
    void contextLoads() {
        LOGGER.info(" Entering ApiGatewayApplicationTests#contextLoads");
        LOGGER.debug(" Entering ApiGatewayApplicationTests#contextLoads with debug context");
    }

    @Test
    void plugAndChargeAdminReadRouteIsRegistered() {
        assertThat(routeRegistry.resolve("pnc")).isEqualTo("http://plug-and-charge-platform:8098");
        assertThat(rbacProperties.getRules())
                .filteredOn(rule -> "pnc-mobility-contract-admin-read".equals(rule.getName()))
                .singleElement()
                .satisfies(rule -> {
                    assertThat(rule.getMethods()).containsExactly("GET");
                    assertThat(rule.getRequiredRoles()).contains("SYSTEM_ADMIN", "ADMIN_READ_ONLY");
                });
    }

    @Test
    void rootCaCeremonySeparatesReadOnlyReadsFromSystemAdminWrites() {
        assertThat(rbacProperties.getRules())
                .filteredOn(rule -> "pnc-root-ca-read".equals(rule.getName()))
                .singleElement()
                .satisfies(rule -> {
                    assertThat(rule.getMethods()).containsExactly("GET");
                    assertThat(rule.getRequiredRoles()).containsExactly("SYSTEM_ADMIN", "ADMIN_READ_ONLY");
                });
        assertThat(rbacProperties.getRules())
                .filteredOn(rule -> "pnc-root-ca-write".equals(rule.getName()))
                .singleElement()
                .satisfies(rule -> {
                    assertThat(rule.getMethods()).containsExactly("POST");
                    assertThat(rule.getRequiredRoles()).containsExactly("SYSTEM_ADMIN");
                });
    }

    @Test
    void subordinateCaAndPcidRoutesSeparateReadsFromMutations() {
        assertPncPkiRule("pnc-mo-subca-read", "GET", "SYSTEM_ADMIN", "ADMIN_READ_ONLY");
        assertPncPkiRule("pnc-mo-subca-write", "POST", "SYSTEM_ADMIN");
        assertPncPkiRule("pnc-pcid-enrollment-read", "GET", "SYSTEM_ADMIN", "ADMIN_READ_ONLY");
        assertPncPkiRule("pnc-pcid-enrollment-write", "POST", "SYSTEM_ADMIN");
        assertPncPkiRule("pnc-pcid-enrollment-read-child", "GET", "SYSTEM_ADMIN", "ADMIN_READ_ONLY");
        assertPncPkiRule("pnc-pcid-enrollment-write-child", "POST", "SYSTEM_ADMIN");
    }

    private void assertPncPkiRule(String name, String method, String... roles) {
        assertThat(rbacProperties.getRules())
                .filteredOn(rule -> name.equals(rule.getName()))
                .singleElement()
                .satisfies(rule -> {
                    assertThat(rule.getMethods()).containsExactly(method);
                    assertThat(rule.getRequiredRoles()).containsExactly(roles);
                    assertThat(rule.isAllowAnonymous()).isFalse();
                });
    }

    @Test
    void taxAdministrationIsRestrictedToSystemAdministrators() {
        assertThat(rbacProperties.getRules())
                .filteredOn(rule -> "pricing-tax-admin".equals(rule.getName()))
                .singleElement()
                .satisfies(rule -> {
                    assertThat(rule.getPathPattern()).isEqualTo("/pricing/api/v1/admin/tax/**");
                    assertThat(rule.getRequiredRoles()).containsExactly("SYSTEM_ADMIN");
                    assertThat(rule.isAllowAnonymous()).isFalse();
                });
    }

    @Test
    void ocpiProtocolBypassesJwtRbacAndReachesTheProtocolBackend() throws Exception {
        int status = mockMvc.perform(get("/ocpi/versions"))
                .andReturn()
                .getResponse()
                .getStatus();

        // The test backend is deliberately unreachable. Any downstream error is
        // acceptable here; a gateway 401/403 would prove OCPI was intercepted.
        assertThat(status).isNotIn(401, 403);
    }
}
