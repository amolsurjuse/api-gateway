package com.electrahub.gateway.security;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TokenVersionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TokenVersionService.class);


    private final StringRedisTemplate redis;
    private final String prefix;

    public TokenVersionService(StringRedisTemplate redis,
                               /**
                                * Executes value for `TokenVersionService`.
                                *
                                * <p>Detailed behavior: follows the current implementation path and
                                * enforces component-specific rules in `com.electrahub.gateway.security`.
                                * @param prefix input consumed by Value.
                                * @return result produced by Value.
                                */
                               @Value("${app.redis.token-version-prefix}") String prefix) {
        LOGGER.debug("Initializing JWT token version service");
        this.redis = redis;
        this.prefix = prefix;
    }

    /**
     * Retrieves get version for `TokenVersionService`.
     *
     * <p>Detailed behavior: follows the current implementation path and
     * enforces component-specific rules in `com.electrahub.gateway.security`.
     * @param userId input consumed by getVersion.
     * @return result produced by getVersion.
     */
    public long getVersion(UUID userId) {
        String v = redis.opsForValue().get(prefix + userId);
        long version = (v == null) ? 0L : Long.parseLong(v);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Resolved token version for uid={} version={}", userId, version);
        }
        return version;
    }
}
