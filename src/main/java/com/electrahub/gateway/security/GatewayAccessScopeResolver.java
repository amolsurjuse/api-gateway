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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class GatewayAccessScopeResolver {

    private final RouteRegistry routeRegistry;
    private final RestClient restClient;
    private final GatewayAccessScopeHeaderSigner signer;
    private final GatewayAccessScopeCache scopeCache;
    private final Duration maxAge;

    public GatewayAccessScopeResolver(
            RouteRegistry routeRegistry,
            RestClient.Builder restClientBuilder,
            GatewayAccessScopeHeaderSigner signer,
            GatewayAccessScopeCache scopeCache,
            @Value("${app.access-context.max-age:30s}") Duration maxAge
    ) {
        this.routeRegistry = routeRegistry;
        this.restClient = restClientBuilder.build();
        this.signer = signer;
        this.scopeCache = scopeCache;
        this.maxAge = maxAge;
    }

    public GatewayAccessScope resolve(HttpServletRequest request) {
        UUID actorId = requireActorId(request);
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization)) {
            throw forbidden("An authenticated administrative session is required.");
        }

        Object tokenVersion = request.getAttribute("tv");
        Object tokenId = request.getAttribute("jti");
        GatewayAccessScopeCache.Lookup lookup = scopeCache.lookup(actorId, tokenVersion, tokenId);
        if (lookup == null) {
            return resolveFreshScope(actorId, authorization);
        }
        if (lookup.scope().isPresent()) {
            return lookup.scope().orElseThrow()
                    .withExpiresAt(expiresAt())
                    .withScopeReference(lookup.scopeReference());
        }

        for (int attempt = 0; attempt < 2; attempt++) {
            GatewayAccessScope expanded = resolveFreshScope(actorId, authorization);
            GatewayAccessScopeCache.StoreResult stored = scopeCache.store(
                    actorId,
                    tokenVersion,
                    tokenId,
                    lookup.generation(),
                    expanded
            );
            if (stored == GatewayAccessScopeCache.StoreResult.STORED) {
                return expanded.withScopeReference(lookup.scopeReference());
            }
            if (stored == GatewayAccessScopeCache.StoreResult.UNAVAILABLE) {
                return expanded;
            }
            lookup = scopeCache.lookup(actorId, tokenVersion, tokenId);
            if (lookup == null) {
                return resolveFreshScope(actorId, authorization);
            }
            if (lookup.scope().isPresent()) {
                return lookup.scope().orElseThrow()
                        .withExpiresAt(expiresAt())
                        .withScopeReference(lookup.scopeReference());
            }
        }
        throw unavailable("Administrative access changed while resolving its scope. Please retry the request.");
    }

    private GatewayAccessScope resolveFreshScope(UUID actorId, String authorization) {
        UserAccessContext userContext = readUserContext(authorization);
        if (!actorId.equals(userContext.actorId())) {
            throw forbidden("The administrative access context did not match the authenticated user.");
        }

        GatewayAccessScope rootScope = buildRootScope(userContext);
        // A scoped administrator without grants must see no tenant data, not an
        // authorization error. Downstream services treat the signed empty scope
        // as an empty result for reads and deny every operational mutation.
        return rootScope.systemAdmin() ? rootScope : expandLocations(rootScope);
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
            String payload = signer.payloadInline(rootScope);
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
                    expiresAt()
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
                expiresAt()
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

    private Instant expiresAt() {
        return Instant.now().plus(maxAge);
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    private record UserAccessContext(UUID actorId, boolean systemAdmin, List<ScopeGrant> grants) {
    }

    private record ScopeGrant(String scopeType, String scopeId, String accessLevel) {
    }

    private record ExpandedLocationScope(Set<String> readLocationIds, Set<String> operateLocationIds) {
    }
}
