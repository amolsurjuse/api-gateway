package com.electrahub.gateway.grpc;

import com.electrahub.proto.user.v1.UserServiceGrpc;
import com.electrahub.proto.user.v1.UserServiceOuterClass;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * gRPC forwarder for User Service.
 * Handles user management endpoints: get user, update profile, change password, etc.
 */
@RestController
@RequestMapping("/user")
@ConditionalOnProperty(name = "app.grpc.forwarders.user.enabled", havingValue = "true")
public class UserGrpcForwarder extends AbstractGrpcForwarder {

    private static final Logger log = LoggerFactory.getLogger(UserGrpcForwarder.class);

    @GrpcClient("user-service")
    private UserServiceGrpc.UserServiceBlockingStub userServiceStub;

    /**
     * Initialize UserGrpcForwarder.
     *
     * @param statusMapper for mapping gRPC status to HTTP
     * @param metadataHelper for creating gRPC metadata
     */
    public UserGrpcForwarder(GrpcStatusToHttpMapper statusMapper, GrpcMetadataHelper metadataHelper) {
        super(statusMapper, metadataHelper);
    }

    /**
     * GET /user/api/v1/users/{userId} - Get user by ID.
     *
     * @param request the HTTP request
     * @param userId the user ID
     * @return user details
     */
    @GetMapping("/api/v1/users/{userId}")
    public ResponseEntity<String> getUser(HttpServletRequest request,
                                         @PathVariable String userId) {
        try {
            UserServiceOuterClass.GetUserRequest grpcRequest = UserServiceOuterClass.GetUserRequest
                    .newBuilder()
                    .setUserId(userId)
                    .build();

            UserServiceOuterClass.UserResponse grpcResponse = userServiceStub.getUser(grpcRequest);
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "getUser");
        } catch (Exception ex) {
            log.error("Error in getUser: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * PUT /user/api/v1/users/{userId}/profile - Update user profile.
     *
     * @param request the HTTP request
     * @param userId the user ID
     * @param body the update request body
     * @return updated user details
     */
    @PutMapping("/api/v1/users/{userId}/profile")
    public ResponseEntity<String> updateProfile(HttpServletRequest request,
                                               @PathVariable String userId,
                                               @RequestBody(required = false) byte[] body) {
        try {
            String json = getRequestBody(body);
            UserServiceOuterClass.UpdateProfileRequest.Builder requestBuilder =
                    UserServiceOuterClass.UpdateProfileRequest.newBuilder()
                    .setUserId(userId);
            parseJsonToProto(json, requestBuilder);

            UserServiceOuterClass.UserResponse grpcResponse = userServiceStub.updateProfile(requestBuilder.build());
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "updateProfile");
        } catch (Exception ex) {
            log.error("Error in updateProfile: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * POST /user/api/v1/users/{userId}/change-password - Change user password.
     *
     * @param request the HTTP request
     * @param userId the user ID
     * @param body the change password request body
     * @return response
     */
    @PostMapping("/api/v1/users/{userId}/change-password")
    public ResponseEntity<String> changePassword(HttpServletRequest request,
                                                @PathVariable String userId,
                                                @RequestBody(required = false) byte[] body) {
        try {
            String json = getRequestBody(body);
            UserServiceOuterClass.ChangePasswordRequest.Builder requestBuilder =
                    UserServiceOuterClass.ChangePasswordRequest.newBuilder()
                    .setUserId(userId);
            parseJsonToProto(json, requestBuilder);

            UserServiceOuterClass.ChangePasswordResponse grpcResponse = userServiceStub.changePassword(requestBuilder.build());
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "changePassword");
        } catch (Exception ex) {
            log.error("Error in changePassword: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * DELETE /user/api/v1/users/{userId} - Delete user account.
     *
     * @param request the HTTP request
     * @param userId the user ID
     * @param body the delete request body
     * @return response
     */
    @DeleteMapping("/api/v1/users/{userId}")
    public ResponseEntity<String> deleteUser(HttpServletRequest request,
                                            @PathVariable String userId,
                                            @RequestBody(required = false) byte[] body) {
        try {
            String json = getRequestBody(body);
            UserServiceOuterClass.DeleteUserRequest.Builder requestBuilder =
                    UserServiceOuterClass.DeleteUserRequest.newBuilder()
                    .setUserId(userId);
            parseJsonToProto(json, requestBuilder);

            UserServiceOuterClass.DeleteUserResponse grpcResponse = userServiceStub.deleteUser(requestBuilder.build());
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "deleteUser");
        } catch (Exception ex) {
            log.error("Error in deleteUser: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * GET /user/api/v1/users - List users (with pagination).
     *
     * @param request the HTTP request
     * @param page the page number (default 0)
     * @param size the page size (default 10)
     * @return list of users
     */
    @GetMapping("/api/v1/users")
    public ResponseEntity<String> listUsers(HttpServletRequest request,
                                           @RequestParam(value = "page", defaultValue = "0") int page,
                                           @RequestParam(value = "size", defaultValue = "10") int size) {
        try {
            UserServiceOuterClass.ListUsersRequest grpcRequest = UserServiceOuterClass.ListUsersRequest
                    .newBuilder()
                    .setPage(page)
                    .setSize(size)
                    .build();

            UserServiceOuterClass.ListUsersResponse grpcResponse = userServiceStub.listUsers(grpcRequest);
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "listUsers");
        } catch (Exception ex) {
            log.error("Error in listUsers: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * GET /user/api/v1/users/{userId}/roles - Get user roles.
     *
     * @param request the HTTP request
     * @param userId the user ID
     * @return user roles
     */
    @GetMapping("/api/v1/users/{userId}/roles")
    public ResponseEntity<String> getUserRoles(HttpServletRequest request,
                                              @PathVariable String userId) {
        try {
            UserServiceOuterClass.GetUserRolesRequest grpcRequest = UserServiceOuterClass.GetUserRolesRequest
                    .newBuilder()
                    .setUserId(userId)
                    .build();

            UserServiceOuterClass.GetUserRolesResponse grpcResponse = userServiceStub.getUserRoles(grpcRequest);
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "getUserRoles");
        } catch (Exception ex) {
            log.error("Error in getUserRoles: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }
}
