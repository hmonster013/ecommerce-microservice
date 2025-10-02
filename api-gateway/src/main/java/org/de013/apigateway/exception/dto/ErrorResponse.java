package org.de013.apigateway.exception.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standard error response format for API Gateway
 * Compatible with ApiResponse format from common module
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
     * Additional metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Create a simple error response
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
     * Create an error response with details
     */
    public static ErrorResponse withDetails(
            int status,
            String error,
            String code,
            String message,
            String details,
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
                .details(details)
                .path(path)
                .method(method)
                .timestamp(LocalDateTime.now())
                .traceId(traceId)
                .build();
    }
}

