package org.de013.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Standard error response format for microservices
 * Compatible with GatewayErrorResponse from API Gateway
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /**
     * Always false for error responses
     */
    private boolean success;

    /**
     * Application-specific error code
     */
    private String code;

    /**
     * Human-readable error message
     */
    private String message;

    /**
     * HTTP status code
     */
    private Integer status;

    /**
     * HTTP status reason phrase
     */
    private String error;

    /**
     * Request path that caused the error
     */
    private String path;

    /**
     * HTTP method of the request
     */
    private String method;

    /**
     * Timestamp when the error occurred
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    /**
     * Trace ID for debugging and correlation
     */
    private String traceId;

    /**
     * Additional error details
     */
    private String details;

    /**
     * Validation errors or additional metadata
     */
    private Map<String, Object> metadata;

    /**
     * Structured validation errors (for validation failures)
     */
    private List<ValidationError> validationErrors;

    /**
     * Nested validation error details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ValidationError {
        /**
         * Field name that failed validation
         */
        private String field;

        /**
         * Rejected value
         */
        private Object rejectedValue;

        /**
         * Validation error message
         */
        private String message;

        /**
         * Validation error code
         */
        private String code;
    }
    
    /**
     * Create a simple error response
     */
    public static ErrorResponse of(
            int status,
            String error,
            String code,
            String message
    ) {
        return ErrorResponse.builder()
                .success(false)
                .status(status)
                .error(error)
                .code(code)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create error response with path
     */
    public static ErrorResponse of(
            int status,
            String error,
            String code,
            String message,
            String path
    ) {
        return ErrorResponse.builder()
                .success(false)
                .status(status)
                .error(error)
                .code(code)
                .message(message)
                .path(path)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create error response with path and method
     */
    public static ErrorResponse of(
            int status,
            String error,
            String code,
            String message,
            String path,
            String method
    ) {
        return ErrorResponse.builder()
                .success(false)
                .status(status)
                .error(error)
                .code(code)
                .message(message)
                .path(path)
                .method(method)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create error response with trace ID
     */
    public static ErrorResponse of(
            int status,
            String error,
            String code,
            String message,
            String path,
            String method,
            String traceId
    ) {
        return ErrorResponse.builder()
                .success(false)
                .status(status)
                .error(error)
                .code(code)
                .message(message)
                .path(path)
                .method(method)
                .timestamp(LocalDateTime.now())
                .traceId(traceId)
                .build();
    }
    
    /**
     * Create error response with details
     */
    public static ErrorResponse withDetails(
            int status,
            String error,
            String code,
            String message,
            String details
    ) {
        return ErrorResponse.builder()
                .success(false)
                .status(status)
                .error(error)
                .code(code)
                .message(message)
                .details(details)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create error response with details and path
     */
    public static ErrorResponse withDetails(
            int status,
            String error,
            String code,
            String message,
            String details,
            String path
    ) {
        return ErrorResponse.builder()
                .success(false)
                .status(status)
                .error(error)
                .code(code)
                .message(message)
                .details(details)
                .path(path)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create error response with metadata (validation errors)
     */
    public static ErrorResponse withMetadata(
            int status,
            String error,
            String code,
            String message,
            Map<String, Object> metadata
    ) {
        return ErrorResponse.builder()
                .success(false)
                .status(status)
                .error(error)
                .code(code)
                .message(message)
                .metadata(metadata)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create error response with metadata and path
     */
    public static ErrorResponse withMetadata(
            int status,
            String error,
            String code,
            String message,
            Map<String, Object> metadata,
            String path
    ) {
        return ErrorResponse.builder()
                .success(false)
                .status(status)
                .error(error)
                .code(code)
                .message(message)
                .metadata(metadata)
                .path(path)
                .timestamp(LocalDateTime.now())
                .build();
    }
}

