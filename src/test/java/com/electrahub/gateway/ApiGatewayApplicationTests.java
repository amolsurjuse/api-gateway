package com.electrahub.gateway;

import com.electrahub.gateway.config.RbacProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "app.security.jwt.secret=test-secret-key-that-is-at-least-64-characters-long-for-hmac-sha-algorithm-testing",
        "app.security.jwt.issuer=auth-service",
        "app.redis.denylist-prefix=deny:jwt:",
        "app.redis.token-version-prefix=tv:",
        "gateway.routes.auth=http://localhost:8080",
        "gateway.routes.user=http://localhost:8082",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"
})
class ApiGatewayApplicationTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiGatewayApplicationTests.class);

    @Autowired
    private RbacProperties rbacProperties;


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
}
