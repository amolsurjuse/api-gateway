package com.electrahub.gateway;

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

    @Test
    void contextLoads() {
    }
}
