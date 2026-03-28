package com.electrahub.gateway.grpc;

import com.electrahub.proto.payment.v1.PaymentServiceGrpc;
import com.electrahub.proto.payment.v1.PaymentServiceOuterClass;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * gRPC forwarder for Payment Service.
 * Handles payment endpoints: initiate payment, confirm payment, get transaction history, etc.
 */
@RestController
@RequestMapping("/payment")
@ConditionalOnProperty(name = "app.grpc.forwarders.payment.enabled", havingValue = "true")
public class PaymentGrpcForwarder extends AbstractGrpcForwarder {

    private static final Logger log = LoggerFactory.getLogger(PaymentGrpcForwarder.class);

    @GrpcClient("payment-service")
    private PaymentServiceGrpc.PaymentServiceBlockingStub paymentServiceStub;

    /**
     * Initialize PaymentGrpcForwarder.
     *
     * @param statusMapper for mapping gRPC status to HTTP
     * @param metadataHelper for creating gRPC metadata
     */
    public PaymentGrpcForwarder(GrpcStatusToHttpMapper statusMapper, GrpcMetadataHelper metadataHelper) {
        super(statusMapper, metadataHelper);
    }

    /**
     * POST /payment/api/v1/transactions/initiate - Initiate a payment transaction.
     *
     * @param request the HTTP request
     * @param body the payment initiation request body
     * @return payment initiation response
     */
    @PostMapping("/api/v1/transactions/initiate")
    public ResponseEntity<String> initiatePayment(HttpServletRequest request,
                                                 @RequestBody(required = false) byte[] body) {
        try {
            String json = getRequestBody(body);
            PaymentServiceOuterClass.InitiatePaymentRequest.Builder requestBuilder =
                    PaymentServiceOuterClass.InitiatePaymentRequest.newBuilder();
            parseJsonToProto(json, requestBuilder);

            PaymentServiceOuterClass.InitiatePaymentResponse grpcResponse = paymentServiceStub.initiatePayment(requestBuilder.build());
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "initiatePayment");
        } catch (Exception ex) {
            log.error("Error in initiatePayment: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * POST /payment/api/v1/transactions/{transactionId}/confirm - Confirm a payment transaction.
     *
     * @param request the HTTP request
     * @param transactionId the transaction ID
     * @param body the payment confirmation request body
     * @return payment confirmation response
     */
    @PostMapping("/api/v1/transactions/{transactionId}/confirm")
    public ResponseEntity<String> confirmPayment(HttpServletRequest request,
                                                @PathVariable String transactionId,
                                                @RequestBody(required = false) byte[] body) {
        try {
            String json = getRequestBody(body);
            PaymentServiceOuterClass.ConfirmPaymentRequest.Builder requestBuilder =
                    PaymentServiceOuterClass.ConfirmPaymentRequest.newBuilder()
                    .setTransactionId(transactionId);
            parseJsonToProto(json, requestBuilder);

            PaymentServiceOuterClass.ConfirmPaymentResponse grpcResponse = paymentServiceStub.confirmPayment(requestBuilder.build());
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "confirmPayment");
        } catch (Exception ex) {
            log.error("Error in confirmPayment: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * GET /payment/api/v1/transactions/{transactionId} - Get transaction details.
     *
     * @param request the HTTP request
     * @param transactionId the transaction ID
     * @return transaction details
     */
    @GetMapping("/api/v1/transactions/{transactionId}")
    public ResponseEntity<String> getTransaction(HttpServletRequest request,
                                                @PathVariable String transactionId) {
        try {
            PaymentServiceOuterClass.GetTransactionRequest grpcRequest =
                    PaymentServiceOuterClass.GetTransactionRequest.newBuilder()
                    .setTransactionId(transactionId)
                    .build();

            PaymentServiceOuterClass.TransactionResponse grpcResponse = paymentServiceStub.getTransaction(grpcRequest);
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "getTransaction");
        } catch (Exception ex) {
            log.error("Error in getTransaction: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * GET /payment/api/v1/users/{userId}/transactions - Get user transaction history.
     *
     * @param request the HTTP request
     * @param userId the user ID
     * @param page the page number (default 0)
     * @param size the page size (default 10)
     * @return list of transactions
     */
    @GetMapping("/api/v1/users/{userId}/transactions")
    public ResponseEntity<String> getTransactionHistory(HttpServletRequest request,
                                                       @PathVariable String userId,
                                                       @RequestParam(value = "page", defaultValue = "0") int page,
                                                       @RequestParam(value = "size", defaultValue = "10") int size) {
        try {
            PaymentServiceOuterClass.GetTransactionHistoryRequest grpcRequest =
                    PaymentServiceOuterClass.GetTransactionHistoryRequest.newBuilder()
                    .setUserId(userId)
                    .setPage(page)
                    .setSize(size)
                    .build();

            PaymentServiceOuterClass.TransactionHistoryResponse grpcResponse = paymentServiceStub.getTransactionHistory(grpcRequest);
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "getTransactionHistory");
        } catch (Exception ex) {
            log.error("Error in getTransactionHistory: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * POST /payment/api/v1/transactions/{transactionId}/refund - Refund a transaction.
     *
     * @param request the HTTP request
     * @param transactionId the transaction ID
     * @param body the refund request body
     * @return refund response
     */
    @PostMapping("/api/v1/transactions/{transactionId}/refund")
    public ResponseEntity<String> refundTransaction(HttpServletRequest request,
                                                   @PathVariable String transactionId,
                                                   @RequestBody(required = false) byte[] body) {
        try {
            String json = getRequestBody(body);
            PaymentServiceOuterClass.RefundRequest.Builder requestBuilder =
                    PaymentServiceOuterClass.RefundRequest.newBuilder()
                    .setTransactionId(transactionId);
            parseJsonToProto(json, requestBuilder);

            PaymentServiceOuterClass.RefundResponse grpcResponse = paymentServiceStub.refundTransaction(requestBuilder.build());
            return createJsonResponse(grpcResponse);
        } catch (StatusRuntimeException ex) {
            return handleGrpcError(ex, "refundTransaction");
        } catch (Exception ex) {
            log.error("Error in refundTransaction: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }
}
