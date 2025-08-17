package org.de013.productcatalog.exception.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Standard error response format for API errors.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /**
     * Timestamp when the error occurred
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    /**
     * HTTP status code
     */
    private Integer status;

    /**
     * HTTP status reason phrase
     */
    private String error;

    /**
     * Application-specific error code
     */
    private String errorCode;

    /**
     * Human-readable error message
     */
    private String message;

    /**
     * Detailed error description
     */
    private String details;

    /**
     * Request path that caused the error
     */
    private String path;

    /**
     * HTTP method of the request
     */
    private String method;

    /**
     * Validation errors (for validation failures)
     */
    private List<ValidationError> validationErrors;

    /**
     * Additional error metadata
     */
    private Map<String, Object> metadata;

    /**
     * Trace ID for debugging (in production, this might be a correlation ID)
     */
    private String traceId;

    /**
     * Nested validation error details
     */
    @Data
    @Builder
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
    public static ErrorResponse of(int status, String error, String message, String path) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status)
                .error(error)
                .message(message)
                .path(path)
                .build();
    }

    /**
     * Create an error response with error code
     */
    public static ErrorResponse of(int status, String error, String errorCode, String message, String path) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status)
                .error(error)
                .errorCode(errorCode)
                .message(message)
                .path(path)
                .build();
    }

    /**
     * Create an error response with validation errors
     */
    public static ErrorResponse withValidationErrors(int status, String message, String path, 
                                                   List<ValidationError> validationErrors) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status)
                .error("Validation Failed")
                .errorCode("VALIDATION_ERROR")
                .message(message)
                .path(path)
                .validationErrors(validationErrors)
                .build();
    }
}
