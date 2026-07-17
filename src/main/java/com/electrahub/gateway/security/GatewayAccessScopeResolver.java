package com.electrahub.gateway.security;

import com.electrahub.gateway.route.RouteRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class GatewayAccessScopeResolver {

    private final RouteRegistry routeRegistry;
    private final RestClient restClient;
    private final GatewayAccessScopeHeaderSigner signer;
    private final Duration cacheTtl;
    private final Duration maxAge;
    private final Map<String, CacheEntry> cache = new HashMap<>();

    public GatewayAccessScopeResolver(
            RouteRegistry routeRegistry,
            RestClient.Builder restClientBuilder,
            GatewayAccessScopeHeaderSigner signer,
            @Value("${app.access-context.cache-ttl:15s}") Duration cacheTtl,
            @Value("${app.access-context.max-age:30s}") Duration maxAge
    ) {
        this.routeRegistry = routeRegistry;
        this.restClient = restClientBuilder.build();
        this.signer = signer;
        this.cacheTtl = cacheTtl;
        this.maxAge = maxAge;
    }

    public GatewayAccessScope resolve(HttpServletRequest request) {
        UUID actorId = requireActorId(request);
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization)) {
            throw forbidden("An authenticated administrative session is required.");
        }

        String cacheKey = actorId + ":" + request.getAttribute("tv") + ":" + request.getAttribute("jti");
        CacheEntry cached = cached(cacheKey);
        if (cached != null) {
            return cached.scope();
        }

        UserAccessContext userContext = readUserContext(authorization);
        if (!actorId.equals(userContext.actorId())) {
            throw forbidden("The administrative access context did not match the authenticated user.");
        }

        GatewayAccessScope rootScope = buildRootScope(userContext);
        if (!rootScope.hasReadScope()) {
            throw forbidden("This account has no administrative scope grants.");
        }

        GatewayAccessScope expanded = rootScope.systemAdmin() ? rootScope : expandLocations(rootScope);
        cache(cacheKey, expanded);
        return expanded;
    }

    private UserAccessContext readUserContext(String authorization) {
        String userService = routeRegistry.resolve("user");
        if (!StringUtils.hasText(userService)) {
            throw unavailable("The user service route is unavailable for access scope resolution.");
        }
        try {
            UserAccessContext response = restClient.get()
                    .uri(userService + "/api/v1/admin/access/me")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .body(UserAccessContext.class);
            if (response == null || response.actorId() == null) {
                throw unavailable("The user service returned an empty access scope.");
            }
            return response;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw unavailable("Could not resolve administrative scope.");
        }
    }

    private GatewayAccessScope expandLocations(GatewayAccessScope rootScope) {
        String chargerService = routeRegistry.resolve("charger-management");
        if (!StringUtils.hasText(chargerService)) {
            throw unavailable("The charger service route is unavailable for scope expansion.");
        }
        try {
            String payload = signer.payload(rootScope);
            ExpandedLocationScope response = restClient.post()
                    .uri(chargerService + "/api/v1/internal/access/expand-locations")
                    .header(GatewayAccessScopeHeaderSigner.CONTEXT_HEADER, payload)
                    .header(GatewayAccessScopeHeaderSigner.SIGNATURE_HEADER, signer.signature(payload))
                    .retrieve()
                    .body(ExpandedLocationScope.class);
            if (response == null) {
                throw unavailable("The charger service returned an empty location scope.");
            }
            return new GatewayAccessScope(
                    rootScope.actorId(),
                    rootScope.systemAdmin(),
                    rootScope.readEnterpriseIds(),
                    rootScope.readNetworkIds(),
                    response.readLocationIds(),
                    rootScope.operateEnterpriseIds(),
                    rootScope.operateNetworkIds(),
                    response.operateLocationIds(),
                    rootScope.expiresAt()
            );
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw unavailable("Could not expand administrative scope to locations.");
        }
    }

    private GatewayAccessScope buildRootScope(UserAccessContext source) {
        Set<String> readEnterpriseIds = new HashSet<>();
        Set<String> readNetworkIds = new HashSet<>();
        Set<String> readLocationIds = new HashSet<>();
        Set<String> operateEnterpriseIds = new HashSet<>();
        Set<String> operateNetworkIds = new HashSet<>();
        Set<String> operateLocationIds = new HashSet<>();

        for (ScopeGrant grant : source.grants() == null ? List.<ScopeGrant>of() : source.grants()) {
            if (grant.scopeType() == null || !StringUtils.hasText(grant.scopeId())) {
                continue;
            }
            select(readEnterpriseIds, readNetworkIds, readLocationIds, grant.scopeType())
                    .add(grant.scopeId().trim().toUpperCase());
            if ("OPERATE".equalsIgnoreCase(grant.accessLevel())) {
                select(operateEnterpriseIds, operateNetworkIds, operateLocationIds, grant.scopeType())
                        .add(grant.scopeId().trim().toUpperCase());
            }
        }
        return new GatewayAccessScope(
                source.actorId(),
                source.systemAdmin(),
                readEnterpriseIds,
                readNetworkIds,
                readLocationIds,
                operateEnterpriseIds,
                operateNetworkIds,
                operateLocationIds,
                Instant.now().plus(maxAge)
        );
    }

    private Set<String> select(Set<String> enterpriseIds, Set<String> networkIds, Set<String> locationIds, String scopeType) {
        return switch (scopeType.trim().toUpperCase()) {
            case "ENTERPRISE" -> enterpriseIds;
            case "NETWORK" -> networkIds;
            case "LOCATION" -> locationIds;
            default -> throw forbidden("The administrative scope contains an unsupported scope type.");
        };
    }

    private UUID requireActorId(HttpServletRequest request) {
        Object value = request.getAttribute("uid");
        if (value == null) {
            throw forbidden("An authenticated administrative session is required.");
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException ex) {
            throw forbidden("The authenticated user identity is invalid.");
        }
    }

    private synchronized CacheEntry cached(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt().isAfter(Instant.now())) {
            return entry;
        }
        cache.remove(key);
        return null;
    }

    private synchronized void cache(String key, GatewayAccessScope scope) {
        if (cache.size() > 10_000) {
            Instant now = Instant.now();
            cache.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        }
        cache.put(key, new CacheEntry(scope, Instant.now().plus(cacheTtl)));
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    private record CacheEntry(GatewayAccessScope scope, Instant expiresAt) {
    }

    private record UserAccessContext(UUID actorId, boolean systemAdmin, List<ScopeGrant> grants) {
    }

    private record ScopeGrant(String scopeType, String scopeId, String accessLevel) {
    }

    private record ExpandedLocationScope(Set<String> readLocationIds, Set<String> operateLocationIds) {
    }
}
