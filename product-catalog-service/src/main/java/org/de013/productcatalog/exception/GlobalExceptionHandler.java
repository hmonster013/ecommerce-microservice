package org.de013.productcatalog.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.de013.productcatalog.exception.dto.ErrorResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler for the Product Catalog Service.
 * Handles all exceptions and converts them to standardized error responses.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle business exceptions
     */
    @ExceptionHandler(BaseBusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BaseBusinessException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        
        if (ex.shouldLogAsError()) {
            log.error("Business exception [{}]: {}", traceId, ex.getMessage(), ex);
        } else if (ex.shouldLogAsWarning()) {
            log.warn("Business exception [{}]: {}", traceId, ex.getMessage());
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(java.time.LocalDateTime.now())
                .status(ex.getHttpStatus().value())
                .error(ex.getHttpStatus().getReasonPhrase())
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(ex.getHttpStatus()).body(errorResponse);
    }

    /**
     * Handle validation errors from @Valid annotations
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        log.warn("Validation exception [{}]: {}", traceId, ex.getMessage());

        List<ErrorResponse.ValidationError> validationErrors = new ArrayList<>();
        
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            ErrorResponse.ValidationError validationError = ErrorResponse.ValidationError.builder()
                    .field(fieldError.getField())
                    .rejectedValue(fieldError.getRejectedValue())
                    .message(fieldError.getDefaultMessage())
                    .code(fieldError.getCode())
                    .build();
            validationErrors.add(validationError);
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(java.time.LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .errorCode("VALIDATION_ERROR")
                .message("Validation failed for one or more fields")
                .details(String.format("Found %d validation errors", validationErrors.size()))
                .path(request.getRequestURI())
                .method(request.getMethod())
                .validationErrors(validationErrors)
                .traceId(traceId)
                .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle constraint violation exceptions
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        log.warn("Constraint violation [{}]: {}", traceId, ex.getMessage());

        List<ErrorResponse.ValidationError> validationErrors = new ArrayList<>();
        
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            ErrorResponse.ValidationError validationError = ErrorResponse.ValidationError.builder()
                    .field(violation.getPropertyPath().toString())
                    .rejectedValue(violation.getInvalidValue())
                    .message(violation.getMessage())
                    .build();
            validationErrors.add(validationError);
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(java.time.LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .errorCode("CONSTRAINT_VIOLATION")
                .message("Constraint validation failed")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .validationErrors(validationErrors)
                .traceId(traceId)
                .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle authentication exceptions
     */
    @ExceptionHandler({AuthenticationException.class, BadCredentialsException.class})
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        log.warn("Authentication failed [{}]: {}", traceId, ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(java.time.LocalDateTime.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error(HttpStatus.UNAUTHORIZED.getReasonPhrase())
                .errorCode("AUTHENTICATION_FAILED")
                .message("Authentication failed")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    /**
     * Handle authorization exceptions
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        log.warn("Access denied [{}]: {}", traceId, ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(java.time.LocalDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error(HttpStatus.FORBIDDEN.getReasonPhrase())
                .errorCode("ACCESS_DENIED")
                .message("Access denied")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    /**
     * Handle data integrity violations (e.g., unique constraint violations)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        
        String traceId = generateTraceId();
        log.error("Data integrity violation [{}]: {}", traceId, ex.getMessage(), ex);

        String message = "Data integrity violation";
        String errorCode = "DATA_INTEGRITY_VIOLATION";
        
        // Try to provide more specific error messages for common cases
        if (ex.getMessage() != null) {
            if (ex.getMessage().contains("unique") || ex.getMessage().contains("duplicate")) {
                message = "Duplicate entry - record already exists";
                errorCode = "DUPLICATE_ENTRY";
            } else if (ex.getMessage().contains("foreign key")) {
                message = "Referenced record does not exist";
                errorCode = "FOREIGN_KEY_VIOLATION";
            }
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(java.time.LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error(HttpStatus.CONFLICT.getReasonPhrase())
                .errorCode(errorCode)
                .message(message)
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /**
     * Handle HTTP method not supported
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupportedException(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.warn("Method not supported [{}]: {}", traceId, ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(java.time.LocalDateTime.now())
                .status(HttpStatus.METHOD_NOT_ALLOWED.value())
                .error(HttpStatus.METHOD_NOT_ALLOWED.getReasonPhrase())
                .errorCode("METHOD_NOT_ALLOWED")
                .message(String.format("HTTP method '%s' is not supported for this endpoint", request.getMethod()))
                .details(String.format("Supported methods: %s", String.join(", ", ex.getSupportedMethods())))
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(errorResponse);
    }

    /**
     * Handle media type not supported
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.warn("Media type not supported [{}]: {}", traceId, ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(java.time.LocalDateTime.now())
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value())
                .error(HttpStatus.UNSUPPORTED_MEDIA_TYPE.getReasonPhrase())
                .errorCode("UNSUPPORTED_MEDIA_TYPE")
                .message("Media type not supported")
                .details(String.format("Supported media types: %s", ex.getSupportedMediaTypes()))
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(errorResponse);
    }

    /**
     * Handle missing request parameters
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameterException(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.warn("Missing request parameter [{}]: {}", traceId, ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(java.time.LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .errorCode("MISSING_PARAMETER")
                .message(String.format("Required parameter '%s' is missing", ex.getParameterName()))
                .details(String.format("Parameter '%s' of type '%s' is required", ex.getParameterName(), ex.getParameterType()))
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(traceId)
                .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle method argument type mismatch
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatchException(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.warn("Type mismatch [{}]: {}", traceId, ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(java.time.LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .errorCode("TYPE_MISMATCH")
                .message(String.format("Invalid value '%s' for parameter '%s'", ex.getValue(), ex.getName()))
                .details(String.format("Expected type: %s", ex.getRequiredType().getSimpleName()))
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(traceId)
                .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle malformed JSON requests
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadableException(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.warn("Message not readable [{}]: {}", traceId, ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(java.time.LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .errorCode("MALFORMED_REQUEST")
                .message("Malformed JSON request")
                .details("Request body could not be parsed")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(traceId)
                .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle 404 - No handler found
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFoundException(
            NoHandlerFoundException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.warn("No handler found [{}]: {}", traceId, ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(java.time.LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                .errorCode("ENDPOINT_NOT_FOUND")
                .message(String.format("No endpoint found for %s %s", ex.getHttpMethod(), ex.getRequestURL()))
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handle all other exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.error("Unexpected error [{}]: {}", traceId, ex.getMessage(), ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(java.time.LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .errorCode("INTERNAL_SERVER_ERROR")
                .message("An unexpected error occurred")
                .details("Please contact support if the problem persists")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .traceId(traceId)
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Generate a unique trace ID for error tracking
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
