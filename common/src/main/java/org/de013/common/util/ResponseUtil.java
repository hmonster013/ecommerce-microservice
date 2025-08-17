package org.de013.common.util;

import org.de013.common.constant.JCode;
import org.de013.common.constant.MessageConstants;
import org.de013.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

/**
 * Utility class for creating standardized API responses
 * Uses JCode constants for consistent response codes
 */
public final class ResponseUtil {

    // Private constructor to prevent instantiation
    private ResponseUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ========== Success Responses ==========

    public static <T> ResponseEntity<ApiResponse<T>> success(T data) {
        return ResponseEntity.ok(buildSuccessResponse(data, MessageConstants.SUCCESS, JCode.OK));
    }

    public static <T> ResponseEntity<ApiResponse<T>> success(T data, String message) {
        return ResponseEntity.ok(buildSuccessResponse(data, message, JCode.OK));
    }

    public static ResponseEntity<ApiResponse<Void>> success(String message) {
        return ResponseEntity.ok(buildSuccessResponse(null, message, JCode.OK));
    }

    public static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return success(data);
    }

    public static <T> ResponseEntity<ApiResponse<T>> created(T data) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(buildSuccessResponse(data, MessageConstants.CREATED, JCode.CREATED));
    }

    public static <T> ResponseEntity<ApiResponse<T>> created(T data, String message) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(buildSuccessResponse(data, message, JCode.CREATED));
    }

    public static <T> ResponseEntity<ApiResponse<T>> updated(T data) {
        return ResponseEntity.ok(buildSuccessResponse(data, MessageConstants.UPDATED, JCode.UPDATED));
    }

    public static <T> ResponseEntity<ApiResponse<T>> updated(T data, String message) {
        return ResponseEntity.ok(buildSuccessResponse(data, message, JCode.UPDATED));
    }

    public static <T> ResponseEntity<ApiResponse<T>> deleted() {
        return ResponseEntity.ok(buildSuccessResponse(null, MessageConstants.DELETED, JCode.DELETED));
    }

    public static <T> ResponseEntity<ApiResponse<T>> deleted(String message) {
        return ResponseEntity.ok(buildSuccessResponse(null, message, JCode.DELETED));
    }

    // ========== Error Responses ==========

    public static <T> ResponseEntity<ApiResponse<T>> badRequest(String message) {
        return ResponseEntity.badRequest()
                .body(buildErrorResponse(message, JCode.BAD_REQUEST));
    }

    public static <T> ResponseEntity<ApiResponse<T>> badRequest(String message, Object errors) {
        return ResponseEntity.badRequest()
                .body(buildErrorResponse(message, JCode.BAD_REQUEST, errors));
    }

    public static <T> ResponseEntity<ApiResponse<T>> unauthorized(String message) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(buildErrorResponse(message, JCode.UNAUTHORIZED));
    }

    public static <T> ResponseEntity<ApiResponse<T>> forbidden(String message) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(buildErrorResponse(message, JCode.FORBIDDEN));
    }

    public static <T> ResponseEntity<ApiResponse<T>> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildErrorResponse(message, JCode.NOT_FOUND));
    }

    public static <T> ResponseEntity<ApiResponse<T>> conflict(String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildErrorResponse(message, JCode.CONFLICT));
    }

    public static <T> ResponseEntity<ApiResponse<T>> unprocessableEntity(String message, Object errors) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(buildErrorResponse(message, JCode.VALIDATION_ERROR, errors));
    }

    public static <T> ResponseEntity<ApiResponse<T>> tooManyRequests(String message) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(buildErrorResponse(message, JCode.TOO_MANY_REQUESTS));
    }

    public static <T> ResponseEntity<ApiResponse<T>> internalServerError(String message) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildErrorResponse(message, JCode.INTERNAL_SERVER_ERROR));
    }

    public static <T> ResponseEntity<ApiResponse<T>> serviceUnavailable(String message) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(buildErrorResponse(message, JCode.SERVICE_UNAVAILABLE));
    }

    // ========== Generic Error Response ==========

    public static <T> ResponseEntity<ApiResponse<T>> error(String message, HttpStatus status, String code) {
        return ResponseEntity.status(status)
                .body(buildErrorResponse(message, code));
    }

    public static <T> ResponseEntity<ApiResponse<T>> error(String message, Object errors, HttpStatus status, String code) {
        return ResponseEntity.status(status)
                .body(buildErrorResponse(message, code, errors));
    }

    // ========== Helper Methods ==========

    private static <T> ApiResponse<T> buildSuccessResponse(T data, String message, String code) {
        return ApiResponse.success(data, message, code);
    }

    private static <T> ApiResponse<T> buildErrorResponse(String message, String code) {
        return ApiResponse.error(message, code);
    }

    private static <T> ApiResponse<T> buildErrorResponse(String message, String code, Object errors) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .errors(errors)
                .timestamp(LocalDateTime.now())
                .build();
    }
}

