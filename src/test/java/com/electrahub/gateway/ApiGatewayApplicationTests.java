package com.electrahub.gateway;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

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
}
