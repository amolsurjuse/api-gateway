package com.electrahub.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenDenylistService {

    private final StringRedisTemplate redis;
    private final String prefix;

    public TokenDenylistService(StringRedisTemplate redis,
                                @Value("${app.redis.denylist-prefix}") String prefix) {
        this.redis = redis;
        this.prefix = prefix;
    }

    public boolean isDenied(String jti) {
        if (jti == null || jti.isBlank()) return false;
        return Boolean.TRUE.equals(redis.hasKey(prefix + jti));
    }
}
