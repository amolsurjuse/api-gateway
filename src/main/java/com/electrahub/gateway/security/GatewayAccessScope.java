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
        Set<String> permissions,
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
                Set.of(),
                null,
                expiresAt
        );
    }

    public GatewayAccessScope(
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
        this(actorId, systemAdmin, readEnterpriseIds, readNetworkIds, readLocationIds,
                operateEnterpriseIds, operateNetworkIds, operateLocationIds, Set.of(), scopeReference, expiresAt);
    }

    public GatewayAccessScope {
        readEnterpriseIds = immutable(readEnterpriseIds);
        readNetworkIds = immutable(readNetworkIds);
        readLocationIds = immutable(readLocationIds);
        operateEnterpriseIds = immutable(operateEnterpriseIds);
        operateNetworkIds = immutable(operateNetworkIds);
        operateLocationIds = immutable(operateLocationIds);
        permissions = immutable(permissions);
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
                permissions,
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
                permissions,
                newScopeReference,
                expiresAt
        );
    }

    public GatewayAccessScope withPermissions(Set<String> newPermissions) {
        return new GatewayAccessScope(actorId, systemAdmin, readEnterpriseIds, readNetworkIds, readLocationIds,
                operateEnterpriseIds, operateNetworkIds, operateLocationIds, newPermissions, scopeReference, expiresAt);
    }

    private static Set<String> immutable(Set<String> values) {
        return values == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(values));
    }
}
