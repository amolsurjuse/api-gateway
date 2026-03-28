package com.electrahub.gateway.grpc;

import com.electrahub.proto.auth.v1.AuthServiceGrpc;
import com.electrahub.proto.auth.v1.AuthServiceOuterClass;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * gRPC forwarder for Auth Service.
 * Handles authentication endpoints: login, register, refresh token, logout, etc.
 */
@RestController
@RequestMapping("/auth")
@ConditionalOnProperty(name = "app.grpc.forwarders.auth.enabled", havingValue = "true")
public class AuthGrpcForwarder extends AbstractGrpcForwarder {

    private static final Logger log = LoggerFactory.getLogger(AuthGrpcForwarder.class);

    @GrpcClient("auth-service")
    private AuthServiceGrpc.AuthServiceBlockingStub authServiceStub;

    /**
     * Initialize AuthGrpcForwarder.
     *
     * @param statusMapper for mapping gRPC status to HTTP
     * @param metadataHelper for creating gRPC metadata
     */
    public AuthGrpcForwarder(GrpcStatusToHttpMapper statusMapper, GrpcMetadataHelper metadataHelper) {
        super(statusMapper, metadataHelper);
    }

    /**
     * POST /auth/api/auth/login - Login user and return access/refresh tokens.
     *
     * @param request the HTTP request
     * @param response the HTTP response
     * @param body the login request body
     * @return login response with tokens
     */
    @PostMapping("/api/auth/login")
    public ResponseEntity<String> login(HttpServletRequest request,
                                        HttpServletResponse response,
                                        @RequestBody(required = false) byte[] body) {
        try {
            String json = getRequestBody(body);
            AuthServiceOuterClass.LoginRequest.Builder requestBuilder =
                    AuthServiceOuterClass.LoginRequest.newBuilder();
            parseJsonToProto(json, requestBuilder);

            AuthServiceOuterClass.LoginResponse grpcResponse = authServiceStub.login(requestBuilder.build());

            // Set cookies from response if present
            if (grpcResponse.hasRefreshToken() && !grpcResponse.getRefreshToken().isBlank()) {
                Cookie cookie = new Cookie("refresh_token", grpcResponse.getRefreshToken());
                cookie.setHttpOnly(true);
                cookie.setSecure(true);
                cookie.setPath("/");
                cookie.setMaxAge((int) grpcResponse.getRefreshTokenExpiresIn());
                response.addCookie(cookie);
            }

            if (grpcResponse.hasDeviceId() && !grpcResponse.getDeviceId().isBlank()) {
                Cookie cookie = new Cookie("device_id", grpcResponse.getDeviceId());
                cookie.setHttpOnly(true);
                cookie.setSecure(true);
                cookie.setPath("/");
                response.addCookie(cookie);
            }

            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "login");
        } catch (Exception ex) {
            log.error("Error in login: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * POST /auth/api/auth/register - Register new user.
     *
     * @param request the HTTP request
     * @param body the registration request body
     * @return registration response
     */
    @PostMapping("/api/auth/register")
    public ResponseEntity<String> register(HttpServletRequest request,
                                          @RequestBody(required = false) byte[] body) {
        try {
            String json = getRequestBody(body);
            AuthServiceOuterClass.RegisterRequest.Builder requestBuilder =
                    AuthServiceOuterClass.RegisterRequest.newBuilder();
            parseJsonToProto(json, requestBuilder);

            AuthServiceOuterClass.RegisterResponse grpcResponse = authServiceStub.register(requestBuilder.build());
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "register");
        } catch (Exception ex) {
            log.error("Error in register: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * POST /auth/api/auth/refresh - Refresh access token.
     *
     * @param request the HTTP request
     * @param body the refresh request body
     * @return refresh response with new tokens
     */
    @PostMapping("/api/auth/refresh")
    public ResponseEntity<String> refreshToken(HttpServletRequest request,
                                              @RequestBody(required = false) byte[] body) {
        try {
            String json = getRequestBody(body);
            AuthServiceOuterClass.RefreshTokenRequest.Builder requestBuilder =
                    AuthServiceOuterClass.RefreshTokenRequest.newBuilder();
            parseJsonToProto(json, requestBuilder);

            AuthServiceOuterClass.RefreshTokenResponse grpcResponse = authServiceStub.refreshToken(requestBuilder.build());
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "refreshToken");
        } catch (Exception ex) {
            log.error("Error in refreshToken: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * POST /auth/api/auth/logout-device - Logout from current device.
     *
     * @param request the HTTP request
     * @param body the logout device request body
     * @return logout response
     */
    @PostMapping("/api/auth/logout-device")
    public ResponseEntity<String> logoutDevice(HttpServletRequest request,
                                              @RequestBody(required = false) byte[] body) {
        try {
            String json = getRequestBody(body);
            AuthServiceOuterClass.LogoutDeviceRequest.Builder requestBuilder =
                    AuthServiceOuterClass.LogoutDeviceRequest.newBuilder();
            parseJsonToProto(json, requestBuilder);

            AuthServiceOuterClass.LogoutResponse grpcResponse = authServiceStub.logoutDevice(requestBuilder.build());
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "logoutDevice");
        } catch (Exception ex) {
            log.error("Error in logoutDevice: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * POST /auth/api/auth/logout-all - Logout from all devices.
     *
     * @param request the HTTP request
     * @param body the logout all request body
     * @return logout response
     */
    @PostMapping("/api/auth/logout-all")
    public ResponseEntity<String> logoutAll(HttpServletRequest request,
                                           @RequestBody(required = false) byte[] body) {
        try {
            String json = getRequestBody(body);
            AuthServiceOuterClass.LogoutAllRequest.Builder requestBuilder =
                    AuthServiceOuterClass.LogoutAllRequest.newBuilder();
            parseJsonToProto(json, requestBuilder);

            AuthServiceOuterClass.LogoutResponse grpcResponse = authServiceStub.logoutAll(requestBuilder.build());
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "logoutAll");
        } catch (Exception ex) {
            log.error("Error in logoutAll: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * GET /auth/api/countries - Get list of countries for registration.
     *
     * @param request the HTTP request
     * @return list of countries
     */
    @GetMapping("/api/countries")
    public ResponseEntity<String> listCountries(HttpServletRequest request) {
        try {
            AuthServiceOuterClass.ListCountriesResponse grpcResponse = authServiceStub.listCountries(
                    AuthServiceOuterClass.ListCountriesRequest.getDefaultInstance()
            );
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "listCountries");
        } catch (Exception ex) {
            log.error("Error in listCountries: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }
}
