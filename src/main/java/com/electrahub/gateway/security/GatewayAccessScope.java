package com.electrahub.gateway.security;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public record GatewayAccessScope(
        UUID actorId,
        boolean systemAdmin,
        Set<String> readEnterpriseIds,
        Set<String> readNetworkIds,
        Set<String> readLocationIds,
        Set<String> operateEnterpriseIds,
        Set<String> operateNetworkIds,
        Set<String> operateLocationIds,
        String scopeReference,
        Instant expiresAt
) {
    public GatewayAccessScope(
            UUID actorId,
            boolean systemAdmin,
            Set<String> readEnterpriseIds,
            Set<String> readNetworkIds,
            Set<String> readLocationIds,
            Set<String> operateEnterpriseIds,
            Set<String> operateNetworkIds,
            Set<String> operateLocationIds,
            Instant expiresAt
    ) {
        this(
                actorId,
                systemAdmin,
                readEnterpriseIds,
                readNetworkIds,
                readLocationIds,
                operateEnterpriseIds,
                operateNetworkIds,
                operateLocationIds,
                null,
                expiresAt
        );
    }

    public GatewayAccessScope {
        readEnterpriseIds = immutable(readEnterpriseIds);
        readNetworkIds = immutable(readNetworkIds);
        readLocationIds = immutable(readLocationIds);
        operateEnterpriseIds = immutable(operateEnterpriseIds);
        operateNetworkIds = immutable(operateNetworkIds);
        operateLocationIds = immutable(operateLocationIds);
    }

    public boolean hasReadScope() {
        return systemAdmin || !readEnterpriseIds.isEmpty() || !readNetworkIds.isEmpty() || !readLocationIds.isEmpty();
    }

    public boolean hasOperateScope() {
        return systemAdmin || !operateEnterpriseIds.isEmpty() || !operateNetworkIds.isEmpty() || !operateLocationIds.isEmpty();
    }

    public GatewayAccessScope withExpiresAt(Instant newExpiresAt) {
        return new GatewayAccessScope(
                actorId,
                systemAdmin,
                readEnterpriseIds,
                readNetworkIds,
                readLocationIds,
                operateEnterpriseIds,
                operateNetworkIds,
                operateLocationIds,
                scopeReference,
                newExpiresAt
        );
    }

    public GatewayAccessScope withScopeReference(String newScopeReference) {
        return new GatewayAccessScope(
                actorId,
                systemAdmin,
                readEnterpriseIds,
                readNetworkIds,
                readLocationIds,
                operateEnterpriseIds,
                operateNetworkIds,
                operateLocationIds,
                newScopeReference,
                expiresAt
        );
    }

    private static Set<String> immutable(Set<String> values) {
        return values == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(values));
    }
}
