package com.electrahub.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Component
public class GatewayAccessScopeHeaderSigner {

    public static final String CONTEXT_HEADER = "X-ElectraHub-Access-Context";
    public static final String SIGNATURE_HEADER = "X-ElectraHub-Access-Context-Signature";

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final byte[] secret;

    public GatewayAccessScopeHeaderSigner(
            ObjectMapper objectMapper,
            @Value("${app.access-context.secret}") String secret,
            @Value("${spring.profiles.active:}") String activeProfiles
    ) {
        this.objectMapper = objectMapper;
        // Production injects this secret from Kubernetes. A deterministic local value
        // keeps local/test contexts bootable without weakening the production boundary.
        boolean missingSecret = secret == null || secret.isBlank() || secret.startsWith("CHANGE_ME");
        if (missingSecret && activeProfiles != null && activeProfiles.toLowerCase().contains("prod")) {
            throw new IllegalStateException("APP_INTERNAL_ACCESS_CONTEXT_SECRET must be configured in production");
        }
        String resolvedSecret = missingSecret ? "electrahub-local-access-context-secret" : secret;
        this.secret = resolvedSecret.getBytes(StandardCharsets.UTF_8);
    }

    public void apply(HttpHeaders headers, GatewayAccessScope scope) {
        String payload = payload(scope);
        headers.set(CONTEXT_HEADER, payload);
        headers.set(SIGNATURE_HEADER, signature(payload));
    }

    public String payload(GatewayAccessScope scope) {
        try {
            Map<String, Object> body = Map.of(
                    "actorId", scope.actorId().toString(),
                    "systemAdmin", scope.systemAdmin(),
                    "readEnterpriseIds", scope.readEnterpriseIds(),
                    "readNetworkIds", scope.readNetworkIds(),
                    "readLocationIds", scope.readLocationIds(),
                    "operateEnterpriseIds", scope.operateEnterpriseIds(),
                    "operateNetworkIds", scope.operateNetworkIds(),
                    "operateLocationIds", scope.operateLocationIds(),
                    "expiresAt", scope.expiresAt().toEpochMilli()
            );
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(body));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize the admin access context", ex);
        }
    }

    public String signature(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not sign the admin access context", ex);
        }
    }
}
