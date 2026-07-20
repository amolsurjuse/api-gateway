package com.electrahub.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Stores resolved administrative scopes outside gateway processes. The cache never stores a bearer
 * token: the token version and JWT ID are reduced to a one-way cache identity.
 */
@Component
public class GatewayAccessScopeCache {

    private static final Logger log = LoggerFactory.getLogger(GatewayAccessScopeCache.class);

    private static final DefaultRedisScript<List> LOOKUP_SCRIPT = new DefaultRedisScript<>("""
            local version = redis.call('GET', KEYS[1]) or '0'
            local scopeKey = ARGV[1] .. version .. ':' .. ARGV[2]
            local scope = redis.call('GET', scopeKey) or ''
            return {version, scope}
            """, List.class);

    private static final DefaultRedisScript<Long> STORE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1]) or '0'
            if current ~= ARGV[1] then
                return 0
            end
            redis.call('SET', ARGV[2], ARGV[3], 'PX', ARGV[4])
            redis.call('SADD', KEYS[2], ARGV[2])
            redis.call('PEXPIRE', KEYS[2], ARGV[4])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> INVALIDATE_USER_SCRIPT = new DefaultRedisScript<>("""
            local version = redis.call('INCR', KEYS[1])
            redis.call('PEXPIRE', KEYS[1], ARGV[1])
            local scopeKeys = redis.call('SMEMBERS', KEYS[2])
            if #scopeKeys > 0 then
                redis.call('DEL', unpack(scopeKeys))
            end
            redis.call('DEL', KEYS[2])
            return version
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;
    private final Duration generationTtl;
    private final String prefix;

    public GatewayAccessScopeCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${app.access-context.cache-ttl:10m}") Duration cacheTtl,
            @Value("${app.access-context.cache-generation-ttl:35d}") Duration generationTtl,
            @Value("${app.access-context.cache-prefix:admin:access-scope:v1:}") String prefix
    ) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.cacheTtl = cacheTtl;
        this.generationTtl = generationTtl;
        this.prefix = normalizePrefix(prefix);
    }

    /**
     * Reads a scope and its generation atomically. A null result means Redis is unavailable and
     * callers should resolve a fresh scope without caching it.
     */
    public Lookup lookup(UUID actorId, Object tokenVersion, Object tokenId) {
        String identity = identity(actorId, tokenVersion, tokenId);
        try {
            List<?> result = redis.execute(
                    LOOKUP_SCRIPT,
                    List.of(generationKey(actorId)),
                    scopePrefix(),
                    identity
            );
            if (result == null || result.size() != 2) {
                throw new IllegalStateException("Unexpected access scope cache response");
            }

            String generation = result.get(0).toString();
            String value = result.get(1) == null ? "" : result.get(1).toString();
            if (value.isBlank()) {
                return Lookup.miss(generation);
            }

            CachedScope cached = objectMapper.readValue(value, CachedScope.class);
            if (!actorId.equals(cached.actorId())) {
                log.warn("Discarded administrative scope cache entry with a mismatched actor");
                return Lookup.miss(generation);
            }
            return Lookup.hit(generation, cached.toScope());
        } catch (Exception ex) {
            log.warn("Administrative scope cache lookup unavailable for actor={}: {}", actorId, ex.getMessage());
            return null;
        }
    }

    /**
     * Stores the scope only when the generation read before resolution is still current. This
     * prevents an in-flight request from reintroducing a scope after a grant removal.
     */
    public StoreResult store(UUID actorId, Object tokenVersion, Object tokenId, String generation, GatewayAccessScope scope) {
        if (!actorId.equals(scope.actorId())) {
            throw new IllegalArgumentException("Cached scope actor must match the authenticated actor");
        }
        try {
            String identity = identity(actorId, tokenVersion, tokenId);
            String scopeKey = scopeKey(generation, identity);
            String value = objectMapper.writeValueAsString(CachedScope.from(scope));
            Long stored = redis.execute(
                    STORE_SCRIPT,
                    List.of(generationKey(actorId), userIndexKey(actorId)),
                    generation,
                    scopeKey,
                    value,
                    Long.toString(cacheTtl.toMillis())
            );
            return Long.valueOf(1L).equals(stored) ? StoreResult.STORED : StoreResult.GENERATION_CHANGED;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize the administrative access scope", ex);
        } catch (Exception ex) {
            log.warn("Administrative scope cache write unavailable for actor={}: {}", actorId, ex.getMessage());
            return StoreResult.UNAVAILABLE;
        }
    }

    /**
     * Invalidates every cached token scope for one administrator. The generation increment is
     * atomic with cache cleanup, so stale in-flight computations cannot be used on later requests.
     */
    public void invalidateUser(UUID actorId) {
        try {
            redis.execute(
                    INVALIDATE_USER_SCRIPT,
                    List.of(generationKey(actorId), userIndexKey(actorId)),
                    Long.toString(generationTtl.toMillis())
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Could not invalidate the administrative access scope", ex);
        }
    }

    private String generationKey(UUID actorId) {
        return prefix + "generation:" + actorId;
    }

    private String userIndexKey(UUID actorId) {
        return prefix + "user:" + actorId + ":keys";
    }

    private String scopePrefix() {
        return prefix + "scope:";
    }

    private String scopeKey(String generation, String identity) {
        return scopePrefix() + generation + ":" + identity;
    }

    private String identity(UUID actorId, Object tokenVersion, Object tokenId) {
        String source = actorId + "\u0000" + normalized(tokenVersion, "0") + "\u0000" + normalized(tokenId, "no-jti");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not build the administrative access cache identity", ex);
        }
    }

    private static String normalized(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.toString().trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private static String normalizePrefix(String configuredPrefix) {
        String normalized = configuredPrefix == null ? "" : configuredPrefix.trim();
        if (normalized.isEmpty()) {
            return "admin:access-scope:v1:";
        }
        return normalized.endsWith(":") ? normalized : normalized + ":";
    }

    public enum StoreResult {
        STORED,
        GENERATION_CHANGED,
        UNAVAILABLE
    }

    public record Lookup(String generation, Optional<GatewayAccessScope> scope) {
        static Lookup hit(String generation, GatewayAccessScope scope) {
            return new Lookup(generation, Optional.of(scope));
        }

        static Lookup miss(String generation) {
            return new Lookup(generation, Optional.empty());
        }
    }

    private record CachedScope(
            UUID actorId,
            boolean systemAdmin,
            Set<String> readEnterpriseIds,
            Set<String> readNetworkIds,
            Set<String> readLocationIds,
            Set<String> operateEnterpriseIds,
            Set<String> operateNetworkIds,
            Set<String> operateLocationIds
    ) {
        static CachedScope from(GatewayAccessScope scope) {
            return new CachedScope(
                    scope.actorId(),
                    scope.systemAdmin(),
                    scope.readEnterpriseIds(),
                    scope.readNetworkIds(),
                    scope.readLocationIds(),
                    scope.operateEnterpriseIds(),
                    scope.operateNetworkIds(),
                    scope.operateLocationIds()
            );
        }

        GatewayAccessScope toScope() {
            return new GatewayAccessScope(
                    actorId,
                    systemAdmin,
                    readEnterpriseIds,
                    readNetworkIds,
                    readLocationIds,
                    operateEnterpriseIds,
                    operateNetworkIds,
                    operateLocationIds,
                    Instant.EPOCH
            );
        }
    }
}
