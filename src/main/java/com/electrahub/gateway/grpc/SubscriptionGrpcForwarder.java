package com.electrahub.gateway.grpc;

import com.electrahub.proto.subscription.v1.SubscriptionServiceGrpc;
import com.electrahub.proto.subscription.v1.SubscriptionServiceOuterClass;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * gRPC forwarder for Subscription Service.
 * Handles subscription endpoints: create, update, cancel, get subscriptions, etc.
 */
@RestController
@RequestMapping("/subscription")
@ConditionalOnProperty(name = "app.grpc.forwarders.subscription.enabled", havingValue = "true")
public class SubscriptionGrpcForwarder extends AbstractGrpcForwarder {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionGrpcForwarder.class);

    @GrpcClient("subscription-service")
    private SubscriptionServiceGrpc.SubscriptionServiceBlockingStub subscriptionServiceStub;

    /**
     * Initialize SubscriptionGrpcForwarder.
     *
     * @param statusMapper for mapping gRPC status to HTTP
     * @param metadataHelper for creating gRPC metadata
     */
    public SubscriptionGrpcForwarder(GrpcStatusToHttpMapper statusMapper, GrpcMetadataHelper metadataHelper) {
        super(statusMapper, metadataHelper);
    }

    /**
     * POST /subscription/api/v1/subscriptions - Create a new subscription.
     *
     * @param request the HTTP request
     * @param body the subscription creation request body
     * @return subscription creation response
     */
    @PostMapping("/api/v1/subscriptions")
    public ResponseEntity<String> createSubscription(HttpServletRequest request,
                                                    @RequestBody(required = false) byte[] body) {
        try {
            String json = getRequestBody(body);
            SubscriptionServiceOuterClass.CreateSubscriptionRequest.Builder requestBuilder =
                    SubscriptionServiceOuterClass.CreateSubscriptionRequest.newBuilder();
            parseJsonToProto(json, requestBuilder);

            SubscriptionServiceOuterClass.SubscriptionResponse grpcResponse = subscriptionServiceStub.createSubscription(requestBuilder.build());
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "createSubscription");
        } catch (Exception ex) {
            log.error("Error in createSubscription: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * GET /subscription/api/v1/subscriptions/{subscriptionId} - Get subscription details.
     *
     * @param request the HTTP request
     * @param subscriptionId the subscription ID
     * @return subscription details
     */
    @GetMapping("/api/v1/subscriptions/{subscriptionId}")
    public ResponseEntity<String> getSubscription(HttpServletRequest request,
                                                 @PathVariable String subscriptionId) {
        try {
            SubscriptionServiceOuterClass.GetSubscriptionRequest grpcRequest =
                    SubscriptionServiceOuterClass.GetSubscriptionRequest.newBuilder()
                    .setSubscriptionId(subscriptionId)
                    .build();

            SubscriptionServiceOuterClass.SubscriptionResponse grpcResponse = subscriptionServiceStub.getSubscription(grpcRequest);
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "getSubscription");
        } catch (Exception ex) {
            log.error("Error in getSubscription: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * PUT /subscription/api/v1/subscriptions/{subscriptionId} - Update subscription.
     *
     * @param request the HTTP request
     * @param subscriptionId the subscription ID
     * @param body the update request body
     * @return updated subscription
     */
    @PutMapping("/api/v1/subscriptions/{subscriptionId}")
    public ResponseEntity<String> updateSubscription(HttpServletRequest request,
                                                    @PathVariable String subscriptionId,
                                                    @RequestBody(required = false) byte[] body) {
        try {
            String json = getRequestBody(body);
            SubscriptionServiceOuterClass.UpdateSubscriptionRequest.Builder requestBuilder =
                    SubscriptionServiceOuterClass.UpdateSubscriptionRequest.newBuilder()
                    .setSubscriptionId(subscriptionId);
            parseJsonToProto(json, requestBuilder);

            SubscriptionServiceOuterClass.SubscriptionResponse grpcResponse = subscriptionServiceStub.updateSubscription(requestBuilder.build());
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "updateSubscription");
        } catch (Exception ex) {
            log.error("Error in updateSubscription: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * DELETE /subscription/api/v1/subscriptions/{subscriptionId} - Cancel subscription.
     *
     * @param request the HTTP request
     * @param subscriptionId the subscription ID
     * @param body the cancellation request body
     * @return cancellation response
     */
    @DeleteMapping("/api/v1/subscriptions/{subscriptionId}")
    public ResponseEntity<String> cancelSubscription(HttpServletRequest request,
                                                    @PathVariable String subscriptionId,
                                                    @RequestBody(required = false) byte[] body) {
        try {
            String json = getRequestBody(body);
            SubscriptionServiceOuterClass.CancelSubscriptionRequest.Builder requestBuilder =
                    SubscriptionServiceOuterClass.CancelSubscriptionRequest.newBuilder()
                    .setSubscriptionId(subscriptionId);
            parseJsonToProto(json, requestBuilder);

            SubscriptionServiceOuterClass.CancelSubscriptionResponse grpcResponse = subscriptionServiceStub.cancelSubscription(requestBuilder.build());
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "cancelSubscription");
        } catch (Exception ex) {
            log.error("Error in cancelSubscription: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * GET /subscription/api/v1/users/{userId}/subscriptions - Get user subscriptions.
     *
     * @param request the HTTP request
     * @param userId the user ID
     * @param page the page number (default 0)
     * @param size the page size (default 10)
     * @return list of subscriptions
     */
    @GetMapping("/api/v1/users/{userId}/subscriptions")
    public ResponseEntity<String> getUserSubscriptions(HttpServletRequest request,
                                                      @PathVariable String userId,
                                                      @RequestParam(value = "page", defaultValue = "0") int page,
                                                      @RequestParam(value = "size", defaultValue = "10") int size) {
        try {
            SubscriptionServiceOuterClass.ListUserSubscriptionsRequest grpcRequest =
                    SubscriptionServiceOuterClass.ListUserSubscriptionsRequest.newBuilder()
                    .setUserId(userId)
                    .setPage(page)
                    .setSize(size)
                    .build();

            SubscriptionServiceOuterClass.ListSubscriptionsResponse grpcResponse = subscriptionServiceStub.listUserSubscriptions(grpcRequest);
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "getUserSubscriptions");
        } catch (Exception ex) {
            log.error("Error in getUserSubscriptions: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * GET /subscription/api/v1/plans - Get available subscription plans.
     *
     * @param request the HTTP request
     * @return list of plans
     */
    @GetMapping("/api/v1/plans")
    public ResponseEntity<String> getPlans(HttpServletRequest request) {
        try {
            SubscriptionServiceOuterClass.GetPlansResponse grpcResponse = subscriptionServiceStub.getPlans(
                    SubscriptionServiceOuterClass.GetPlansRequest.getDefaultInstance()
            );
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "getPlans");
        } catch (Exception ex) {
            log.error("Error in getPlans: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }
}
