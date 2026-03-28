package com.electrahub.gateway.grpc;

import com.electrahub.proto.pricing.v1.PricingServiceGrpc;
import com.electrahub.proto.pricing.v1.PricingServiceOuterClass;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * gRPC forwarder for Pricing Service.
 * Handles pricing endpoints: get rates, calculate price, list pricing rules, etc.
 */
@RestController
@RequestMapping("/pricing")
@ConditionalOnProperty(name = "app.grpc.forwarders.pricing.enabled", havingValue = "true")
public class PricingGrpcForwarder extends AbstractGrpcForwarder {

    private static final Logger log = LoggerFactory.getLogger(PricingGrpcForwarder.class);

    @GrpcClient("pricing-service")
    private PricingServiceGrpc.PricingServiceBlockingStub pricingServiceStub;

    /**
     * Initialize PricingGrpcForwarder.
     *
     * @param statusMapper for mapping gRPC status to HTTP
     * @param metadataHelper for creating gRPC metadata
     */
    public PricingGrpcForwarder(GrpcStatusToHttpMapper statusMapper, GrpcMetadataHelper metadataHelper) {
        super(statusMapper, metadataHelper);
    }

    /**
     * GET /pricing/api/v1/rates - Get current pricing rates.
     *
     * @param request the HTTP request
     * @return pricing rates
     */
    @GetMapping("/api/v1/rates")
    public ResponseEntity<String> getRates(HttpServletRequest request) {
        try {
            PricingServiceOuterClass.GetRatesResponse grpcResponse = pricingServiceStub.getRates(
                    PricingServiceOuterClass.GetRatesRequest.getDefaultInstance()
            );
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "getRates");
        } catch (Exception ex) {
            log.error("Error in getRates: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * POST /pricing/api/v1/calculate - Calculate pricing for a session.
     *
     * @param request the HTTP request
     * @param body the calculation request body
     * @return calculated price
     */
    @PostMapping("/api/v1/calculate")
    public ResponseEntity<String> calculatePrice(HttpServletRequest request,
                                                @RequestBody(required = false) byte[] body) {
        try {
            String json = getRequestBody(body);
            PricingServiceOuterClass.CalculatePriceRequest.Builder requestBuilder =
                    PricingServiceOuterClass.CalculatePriceRequest.newBuilder();
            parseJsonToProto(json, requestBuilder);

            PricingServiceOuterClass.CalculatePriceResponse grpcResponse = pricingServiceStub.calculatePrice(requestBuilder.build());
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "calculatePrice");
        } catch (Exception ex) {
            log.error("Error in calculatePrice: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * GET /pricing/api/v1/rules - Get pricing rules.
     *
     * @param request the HTTP request
     * @param page page number (default 0)
     * @param size page size (default 10)
     * @return list of pricing rules
     */
    @GetMapping("/api/v1/rules")
    public ResponseEntity<String> listRules(HttpServletRequest request,
                                           @RequestParam(value = "page", defaultValue = "0") int page,
                                           @RequestParam(value = "size", defaultValue = "10") int size) {
        try {
            PricingServiceOuterClass.ListRulesRequest grpcRequest =
                    PricingServiceOuterClass.ListRulesRequest.newBuilder()
                    .setPage(page)
                    .setSize(size)
                    .build();

            PricingServiceOuterClass.ListRulesResponse grpcResponse = pricingServiceStub.listRules(grpcRequest);
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "listRules");
        } catch (Exception ex) {
            log.error("Error in listRules: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * GET /pricing/api/v1/rules/{ruleId} - Get pricing rule details.
     *
     * @param request the HTTP request
     * @param ruleId the rule ID
     * @return rule details
     */
    @GetMapping("/api/v1/rules/{ruleId}")
    public ResponseEntity<String> getRule(HttpServletRequest request,
                                         @PathVariable String ruleId) {
        try {
            PricingServiceOuterClass.GetRuleRequest grpcRequest =
                    PricingServiceOuterClass.GetRuleRequest.newBuilder()
                    .setRuleId(ruleId)
                    .build();

            PricingServiceOuterClass.RuleResponse grpcResponse = pricingServiceStub.getRule(grpcRequest);
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "getRule");
        } catch (Exception ex) {
            log.error("Error in getRule: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * GET /pricing/api/v1/discounts - Get available discounts.
     *
     * @param request the HTTP request
     * @param userId the user ID (optional)
     * @return list of discounts
     */
    @GetMapping("/api/v1/discounts")
    public ResponseEntity<String> getDiscounts(HttpServletRequest request,
                                              @RequestParam(value = "userId", required = false) String userId) {
        try {
            PricingServiceOuterClass.GetDiscountsRequest.Builder requestBuilder =
                    PricingServiceOuterClass.GetDiscountsRequest.newBuilder();
            if (userId != null && !userId.isBlank()) {
                requestBuilder.setUserId(userId);
            }

            PricingServiceOuterClass.GetDiscountsResponse grpcResponse = pricingServiceStub.getDiscounts(requestBuilder.build());
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "getDiscounts");
        } catch (Exception ex) {
            log.error("Error in getDiscounts: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * POST /pricing/api/v1/pricing-session - Get pricing for a potential session.
     *
     * @param request the HTTP request
     * @param body the pricing session request body
     * @return pricing session response
     */
    @PostMapping("/api/v1/pricing-session")
    public ResponseEntity<String> getPricingSession(HttpServletRequest request,
                                                   @RequestBody(required = false) byte[] body) {
        try {
            String json = getRequestBody(body);
            PricingServiceOuterClass.GetPricingSessionRequest.Builder requestBuilder =
                    PricingServiceOuterClass.GetPricingSessionRequest.newBuilder();
            parseJsonToProto(json, requestBuilder);

            PricingServiceOuterClass.PricingSessionResponse grpcResponse = pricingServiceStub.getPricingSession(requestBuilder.build());
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "getPricingSession");
        } catch (Exception ex) {
            log.error("Error in getPricingSession: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }
}
