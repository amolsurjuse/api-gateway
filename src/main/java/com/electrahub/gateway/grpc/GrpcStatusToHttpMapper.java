package com.electrahub.gateway.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Maps gRPC Status codes to HTTP status codes and formats error responses.
 */
@Component
public class GrpcStatusToHttpMapper {

    /**
     * Convert gRPC Status to HTTP HttpStatus.
     *
     * @param status the gRPC Status
     * @return the corresponding HttpStatus
     */
    public HttpStatus toHttpStatus(Status status) {
        return switch (status.getCode()) {
            case OK -> HttpStatus.OK;
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ALREADY_EXISTS -> HttpStatus.CONFLICT;
            case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
            case RESOURCE_EXHAUSTED -> HttpStatus.TOO_MANY_REQUESTS;
            case FAILED_PRECONDITION, ABORTED, OUT_OF_RANGE -> HttpStatus.BAD_REQUEST;
            case UNIMPLEMENTED -> HttpStatus.NOT_IMPLEMENTED;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case DATA_LOSS -> HttpStatus.INTERNAL_SERVER_ERROR;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case UNKNOWN, CANCELLED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /**
     * Extract error message from a StatusRuntimeException.
     *
     * @param ex the StatusRuntimeException
     * @return the error message
     */
    public String extractErrorMessage(StatusRuntimeException ex) {
        String description = ex.getStatus().getDescription();
        if (description != null && !description.isBlank()) {
            return description;
        }
        return ex.getStatus().getCode().name();
    }

    /**
     * Create a JSON error response body.
     *
     * @param message the error message
     * @param code the error code
     * @return the error response as JSON bytes
     */
    public byte[] formatErrorResponse(String message, String code) {
        String json = "{\"error\":\"" + escapeJson(message) + "\",\"code\":\"" + escapeJson(code) + "\"}";
        return json.getBytes();
    }

    /**
     * Create a response entity from a StatusRuntimeException.
     *
     * @param ex the StatusRuntimeException
     * @return the ResponseEntity with appropriate status and error body
     */
    public ResponseEntity<byte[]> toErrorResponse(StatusRuntimeException ex) {
        HttpStatus httpStatus = toHttpStatus(ex.getStatus());
        String message = extractErrorMessage(ex);
        byte[] body = formatErrorResponse(message, ex.getStatus().getCode().name());
        return ResponseEntity.status(httpStatus).body(body);
    }

    /**
     * Escape JSON special characters in a string.
     *
     * @param str the string to escape
     * @return the escaped string
     */
    private String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
