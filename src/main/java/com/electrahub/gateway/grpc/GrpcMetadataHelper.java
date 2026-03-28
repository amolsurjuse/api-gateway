package com.electrahub.gateway.grpc;

import io.grpc.Metadata;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Utility for propagating HTTP headers to gRPC metadata.
 */
@Component
public class GrpcMetadataHelper {

    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> REQUEST_ID_KEY =
            Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> USER_ID_KEY =
            Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER);

    /**
     * Create gRPC metadata from HTTP request headers.
     *
     * @param request the HTTP servlet request
     * @return the populated Metadata object
     */
    public Metadata createMetadata(HttpServletRequest request) {
        Metadata metadata = new Metadata();

        // Propagate Authorization header
        String authorization = request.getHeader("Authorization");
        if (authorization != null && !authorization.isBlank()) {
            metadata.put(AUTHORIZATION_KEY, authorization);
        }

        // Propagate X-Request-Id
        String requestId = request.getHeader("X-Request-Id");
        if (requestId != null && !requestId.isBlank()) {
            metadata.put(REQUEST_ID_KEY, requestId);
        }

        // Extract user ID from SecurityContext if available
        String userId = extractUserIdFromSecurityContext();
        if (userId != null && !userId.isBlank()) {
            metadata.put(USER_ID_KEY, userId);
        }

        return metadata;
    }

    /**
     * Extract user ID from the SecurityContext.
     *
     * @return the user ID or null if not available
     */
    private String extractUserIdFromSecurityContext() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                // If principal has a user ID claim, extract it
                // This assumes the JWT filter has set the principal or a custom principal with user ID
                Object principal = auth.getPrincipal();
                if (principal instanceof String) {
                    return (String) principal;
                }
            }
        } catch (Exception e) {
            // Silently ignore; user ID is optional
        }
        return null;
    }
}
