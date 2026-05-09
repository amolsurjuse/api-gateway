package com.electrahub.gateway.security;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenDenylistService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TokenDenylistService.class);


    private final StringRedisTemplate redis;
    private final String prefix;

    public TokenDenylistService(StringRedisTemplate redis,
                                /**
                                 * Executes value for `TokenDenylistService`.
                                 *
                                 * <p>Detailed behavior: follows the current implementation path and
                                 * enforces component-specific rules in `com.electrahub.gateway.security`.
                                 * @param prefix input consumed by Value.
                                 * @return result produced by Value.
                                 */
                                @Value("${app.redis.denylist-prefix}") String prefix) {
                                    LOGGER.info(" Entering TokenDenylistService#Value");
                                    LOGGER.debug(" Entering TokenDenylistService#Value with debug context");
        this.redis = redis;
        this.prefix = prefix;
    }

    /**
     * Executes is denied for `TokenDenylistService`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.security`.
     * @param jti input consumed by isDenied.
     * @return result produced by isDenied.
     */
    public boolean isDenied(String jti) {
        if (jti == null || jti.isBlank()) return false;
        return Boolean.TRUE.equals(redis.hasKey(prefix + jti));
    }
}
