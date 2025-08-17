package org.de013.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Standard API response wrapper (Alias for ApiResponse)
 * Provides consistent response structure across all endpoints
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseResponse<T> {
    
    private boolean success;
    private String message;
    private T data;
    private Object errors;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    private String path;
    
    public static <T> BaseResponse<T> success(T data) {
        return BaseResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static <T> BaseResponse<T> success(T data, String message) {
        return BaseResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static <T> BaseResponse<T> error(String message) {
        return BaseResponse.<T>builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static <T> BaseResponse<T> error(String message, Object errors) {
        return BaseResponse.<T>builder()
                .success(false)
                .message(message)
                .errors(errors)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static <T> BaseResponse<T> error(String message, String path) {
        return BaseResponse.<T>builder()
                .success(false)
                .message(message)
                .path(path)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Convert from ApiResponse to BaseResponse
     */
    public static <T> BaseResponse<T> from(ApiResponse<T> apiResponse) {
        return BaseResponse.<T>builder()
                .success(apiResponse.isSuccess())
                .message(apiResponse.getMessage())
                .data(apiResponse.getData())
                .errors(apiResponse.getErrors())
                .timestamp(apiResponse.getTimestamp())
                .path(apiResponse.getPath())
                .build();
    }
    
    /**
     * Convert to ApiResponse
     */
    public ApiResponse<T> toApiResponse() {
        return ApiResponse.<T>builder()
                .success(this.success)
                .message(this.message)
                .data(this.data)
                .errors(this.errors)
                .timestamp(this.timestamp)
                .path(this.path)
                .build();
    }
}
