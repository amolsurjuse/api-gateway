package com.electrahub.gateway.grpc;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/**
 * Base class for service-specific gRPC forwarders with common logic for:
 * - JSON body parsing to proto
 * - Proto response serialization to JSON
 * - Error handling
 * - Header propagation
 */
public abstract class AbstractGrpcForwarder {

    protected static final Logger log = LoggerFactory.getLogger(AbstractGrpcForwarder.class);

    protected final GrpcStatusToHttpMapper statusMapper;
    protected final GrpcMetadataHelper metadataHelper;

    protected AbstractGrpcForwarder(GrpcStatusToHttpMapper statusMapper, GrpcMetadataHelper metadataHelper) {
        this.statusMapper = statusMapper;
        this.metadataHelper = metadataHelper;
    }

    /**
     * Parse JSON request body to a protobuf message builder.
     *
     * @param json the JSON string
     * @param builder the proto message builder
     * @return the populated builder
     * @throws Exception if JSON parsing fails
     */
    protected void parseJsonToProto(String json, Message.Builder builder) throws Exception {
        if (json == null || json.isBlank()) {
            return;
        }
        JsonFormat.parser().ignoringUnknownFields().merge(json, builder);
    }

    /**
     * Serialize a protobuf message to JSON string.
     *
     * @param message the proto message
     * @return the JSON string
     * @throws Exception if serialization fails
     */
    protected String protoToJson(Message message) throws Exception {
        return JsonFormat.printer().print(message);
    }

    /**
     * Create a ResponseEntity from a protobuf message.
     *
     * @param message the proto message
     * @return the ResponseEntity with JSON content type
     */
    protected ResponseEntity<String> createJsonResponse(Message message) throws Exception {
        String json = protoToJson(message);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return ResponseEntity.ok()
                .headers(headers)
                .body(json);
    }

    /**
     * Create a ResponseEntity from a protobuf message with custom status code.
     *
     * @param message the proto message
     * @param status the HTTP status code
     * @return the ResponseEntity with JSON content type
     */
    protected ResponseEntity<String> createJsonResponse(Message message, int statusCode) throws Exception {
        String json = protoToJson(message);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return ResponseEntity.status(statusCode)
                .headers(headers)
                .body(json);
    }

    /**
     * Handle a gRPC StatusRuntimeException and return an error response.
     *
     * @param ex the StatusRuntimeException
     * @param operation the operation name for logging
     * @return the error ResponseEntity
     */
    protected ResponseEntity<String> handleGrpcError(StatusRuntimeException ex, String operation) {
        String message = statusMapper.extractErrorMessage(ex);
        log.error("gRPC error in {}: {} - {}", operation, ex.getStatus().getCode(), message);

        ResponseEntity<byte[]> errorResponse = statusMapper.toErrorResponse(ex);
        String errorBody = new String(errorResponse.getBody(), StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        return ResponseEntity.status(errorResponse.getStatusCode())
                .headers(headers)
                .body(errorBody);
    }

    /**
     * Extract request body as string.
     *
     * @param body the request body bytes
     * @return the body as string or empty string if null
     */
    protected String getRequestBody(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    /**
     * Get gRPC metadata from HTTP request.
     *
     * @param request the HTTP servlet request
     * @return the gRPC metadata
     */
    protected io.grpc.Metadata getGrpcMetadata(HttpServletRequest request) {
        return metadataHelper.createMetadata(request);
    }
}
