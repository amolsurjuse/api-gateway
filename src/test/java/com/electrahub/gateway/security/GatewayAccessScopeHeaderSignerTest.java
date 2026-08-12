package com.electrahub.gateway.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayAccessScopeHeaderSignerTest {

    @Test
    void emitsOnlyAnOpaqueReferenceForCachedScopes() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        GatewayAccessScopeHeaderSigner signer = new GatewayAccessScopeHeaderSigner(
                objectMapper,
                "test-access-context-secret",
                "test"
        );
        GatewayAccessScope scope = new GatewayAccessScope(
                UUID.randomUUID(),
                false,
                Set.of("ENT-1"),
                Set.of("NET-1"),
                Set.of("LOC-1"),
                Set.of(),
                Set.of(),
                Set.of(),
                "5:" + "a".repeat(43),
                Instant.now().plusSeconds(30)
        ).withPermissions(Set.of("ANALYTICS_DRIVER_PII_READ"));

        String payload = signer.payload(scope);
        Map<String, Object> body = objectMapper.readValue(
                Base64.getUrlDecoder().decode(payload),
                new TypeReference<>() { }
        );

        assertThat(body).containsEntry("version", 2);
        assertThat(body).containsEntry("scopeRef", scope.scopeReference());
        assertThat(body).containsEntry("permissions", java.util.List.of("ANALYTICS_DRIVER_PII_READ"));
        assertThat(body).doesNotContainKeys(
                "readEnterpriseIds",
                "readNetworkIds",
                "readLocationIds",
                "operateEnterpriseIds",
                "operateNetworkIds",
                "operateLocationIds"
        );
        assertThat(signer.signature(payload)).isNotBlank();
    }

    @Test
    void keepsTheLegacyPayloadWhenNoRedisReferenceExists() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        GatewayAccessScopeHeaderSigner signer = new GatewayAccessScopeHeaderSigner(
                objectMapper,
                "test-access-context-secret",
                "test"
        );
        GatewayAccessScope scope = new GatewayAccessScope(
                UUID.randomUUID(),
                false,
                Set.of(),
                Set.of(),
                Set.of("LOC-1"),
                Set.of(),
                Set.of(),
                Set.of(),
                Instant.now().plusSeconds(30)
        ).withPermissions(Set.of("ANALYTICS_DRIVER_PII_READ"));

        String payload = signer.payload(scope);
        Map<String, Object> body = objectMapper.readValue(
                new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8),
                new TypeReference<>() { }
        );

        assertThat(body).containsEntry("readLocationIds", java.util.List.of("LOC-1"));
        assertThat(body).containsEntry("permissions", java.util.List.of("ANALYTICS_DRIVER_PII_READ"));
        assertThat(body).doesNotContainKey("version");
    }

    @Test
    void signsShortLivedTenantIdentityContext() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        GatewayAccessScopeHeaderSigner signer = new GatewayAccessScopeHeaderSigner(
                objectMapper,
                "test-access-context-secret",
                "test"
        );
        Instant expiresAt = Instant.now().plusSeconds(60);

        String payload = signer.identityPayload(
                "user-1", "tenant-a", java.util.List.of("DRIVER"), expiresAt);
        Map<String, Object> body = objectMapper.readValue(
                Base64.getUrlDecoder().decode(payload),
                new TypeReference<>() { }
        );

        assertThat(body).containsEntry("version", 1);
        assertThat(body).containsEntry("userId", "user-1");
        assertThat(body).containsEntry("tenantId", "tenant-a");
        assertThat(body).containsEntry("roles", java.util.List.of("DRIVER"));
        assertThat(body).containsEntry("expiresAt", expiresAt.toEpochMilli());
        assertThat(signer.signature(payload)).isNotBlank();
    }
}
