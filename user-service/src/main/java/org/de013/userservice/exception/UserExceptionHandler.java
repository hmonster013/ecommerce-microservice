package org.de013.userservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.de013.common.dto.ApiResponse;
import org.de013.common.exception.BusinessException;
import org.de013.common.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * User service specific exception handler
 * Handles validation errors, business exceptions, and security exceptions
 */
@RestControllerAdvice("org.de013.userservice")
@Slf4j
public class UserExceptionHandler {

    /**
     * Handle validation errors from @Valid annotations
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Validation failed for request {}: {}", request.getDescription(false), errors);

        ApiResponse<Object> response = ApiResponse.<Object>builder()
                .success(false)
                .message("Validation failed")
                .errors(errors)
                .code(HttpStatus.BAD_REQUEST.name())
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(java.time.LocalDateTime.now())
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handle constraint violation exceptions
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolationException(
            ConstraintViolationException ex, WebRequest request) {
        
        Map<String, String> errors = new HashMap<>();
        Set<ConstraintViolation<?>> violations = ex.getConstraintViolations();
        
        for (ConstraintViolation<?> violation : violations) {
            String fieldName = violation.getPropertyPath().toString();
            String errorMessage = violation.getMessage();
            errors.put(fieldName, errorMessage);
        }

        log.warn("Constraint violation for request {}: {}", request.getDescription(false), errors);

        ApiResponse<Object> response = ApiResponse.<Object>builder()
                .success(false)
                .message("Validation failed")
                .errors(errors)
                .code(HttpStatus.BAD_REQUEST.name())
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(java.time.LocalDateTime.now())
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handle business exceptions
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(
            BusinessException ex, WebRequest request) {
        
        log.warn("Business exception for request {}: {}", request.getDescription(false), ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error(ex.getMessage(), HttpStatus.BAD_REQUEST.name());

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handle resource not found exceptions
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {
        
        log.warn("Resource not found for request {}: {}", request.getDescription(false), ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error(ex.getMessage(), HttpStatus.NOT_FOUND.name());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handle user not found exceptions
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleUserNotFoundException(
            UserNotFoundException ex, WebRequest request) {

        log.warn("User not found for request {}: {}", request.getDescription(false), ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error(ex.getMessage(), HttpStatus.NOT_FOUND.name());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handle email already exists exceptions
     */
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Object>> handleEmailAlreadyExistsException(
            EmailAlreadyExistsException ex, WebRequest request) {

        log.warn("Email already exists for request {}: {}", request.getDescription(false), ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error(ex.getMessage(), HttpStatus.CONFLICT.name());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * Handle invalid credentials exceptions
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidCredentialsException(
            InvalidCredentialsException ex, WebRequest request) {

        log.warn("Invalid credentials for request {}: {}", request.getDescription(false), ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error(ex.getMessage(), HttpStatus.UNAUTHORIZED.name());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * Handle invalid token exceptions
     */
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidTokenException(
            InvalidTokenException ex, WebRequest request) {

        log.warn("Invalid token for request {}: {}", request.getDescription(false), ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error(ex.getMessage(), HttpStatus.UNAUTHORIZED.name());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * Handle authentication exceptions (fallback for other auth exceptions)
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadCredentialsException(
            BadCredentialsException ex, WebRequest request) {

        log.warn("Authentication failed for request {}: {}", request.getDescription(false), ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("Invalid username or password", HttpStatus.UNAUTHORIZED.name());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * Handle JWT authentication exceptions
     */
    @ExceptionHandler(JwtAuthenticationException.class)
    public ResponseEntity<ApiResponse<Object>> handleJwtAuthenticationException(
            JwtAuthenticationException ex, WebRequest request) {

        log.warn("JWT authentication exception for request {}: {}", request.getDescription(false), ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error(ex.getMessage(), HttpStatus.UNAUTHORIZED.name());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * Handle account status exceptions
     */
    @ExceptionHandler(AccountStatusException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccountStatusException(
            AccountStatusException ex, WebRequest request) {

        log.warn("Account status exception for request {}: {}", request.getDescription(false), ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error(ex.getMessage(), HttpStatus.FORBIDDEN.name());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * Handle general authentication exceptions (fallback)
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthenticationException(
            AuthenticationException ex, WebRequest request) {

        log.warn("Authentication exception for request {}: {}", request.getDescription(false), ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("Authentication failed", HttpStatus.UNAUTHORIZED.name());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * Handle access denied exceptions
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDeniedException(
            AccessDeniedException ex, WebRequest request) {
        
        log.warn("Access denied for request {}: {}", request.getDescription(false), ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error("Access denied", HttpStatus.FORBIDDEN.name());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * Handle missing request parameter exceptions
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException ex, WebRequest request) {

        log.warn("Missing request parameter for request {}: {}", request.getDescription(false), ex.getMessage());

        String message = "Required parameter '" + ex.getParameterName() + "' is missing";
        ApiResponse<Object> response = ApiResponse.error(message, HttpStatus.BAD_REQUEST.name());

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handle method argument type mismatch exceptions
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException ex, WebRequest request) {

        log.warn("Method argument type mismatch for request {}: {}", request.getDescription(false), ex.getMessage());

        String message = "Invalid value for parameter '" + ex.getName() + "': " + ex.getValue();
        ApiResponse<Object> response = ApiResponse.error(message, HttpStatus.BAD_REQUEST.name());

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handle no handler found exceptions (404)
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoHandlerFoundException(
            NoHandlerFoundException ex, WebRequest request) {

        log.warn("No handler found for request {}: {}", request.getDescription(false), ex.getMessage());

        String message = "Endpoint not found: " + ex.getHttpMethod() + " " + ex.getRequestURL();
        ApiResponse<Object> response = ApiResponse.error(message, HttpStatus.NOT_FOUND.name());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handle all other exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGlobalException(
            Exception ex, WebRequest request) {

        log.error("Unexpected error for request {}: {}", request.getDescription(false), ex.getMessage(), ex);

        ApiResponse<Object> response = ApiResponse.error("An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR.name());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
