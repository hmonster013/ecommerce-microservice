package org.de013.paymentservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.dto.ErrorResponse;
import org.de013.common.exception.BusinessException;
import org.de013.common.exception.ResourceNotFoundException;
import org.de013.paymentservice.constant.PaymentConstants;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Payment Service specific exception handler
 * Handles payment-specific exceptions and Stripe API exceptions
 */
@RestControllerAdvice("org.de013.paymentservice")
@Slf4j
public class PaymentExceptionHandler {

    // ========== VALIDATION EXCEPTIONS ==========

    /**
     * Handle validation errors from @Valid annotations
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, Object> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        String traceId = generateTraceId();
        log.warn("Validation failed [{}] for {}: {}", traceId, request.getRequestURI(), errors);

        ErrorResponse response = ErrorResponse.withMetadata(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "VALIDATION_ERROR",
                "Validation failed",
                errors,
                request.getRequestURI()
        );

        return ResponseEntity.badRequest().body(response);
    }

    // ========== PAYMENT SPECIFIC EXCEPTIONS ==========

    /**
     * Handle payment processing exceptions
     */
    @ExceptionHandler(PaymentProcessingException.class)
    public ResponseEntity<ErrorResponse> handlePaymentProcessingException(
            PaymentProcessingException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.error("Payment processing error [{}]: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "PAYMENT_PROCESSING_ERROR",
                ex.getMessage(),
                request.getRequestURI(),
                request.getMethod(),
                traceId
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handle payment gateway exceptions
     */
    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<ErrorResponse> handlePaymentGatewayException(
            PaymentGatewayException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.error("Payment gateway error [{}]: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "PAYMENT_GATEWAY_ERROR",
                ex.getMessage(),
                request.getRequestURI(),
                request.getMethod(),
                traceId
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Handle payment not found exceptions
     */
    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePaymentNotFoundException(
            PaymentNotFoundException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.warn("Payment not found [{}]: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                "PAYMENT_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI(),
                request.getMethod(),
                traceId
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handle payment method not found exceptions
     */
    @ExceptionHandler(PaymentMethodNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePaymentMethodNotFoundException(
            PaymentMethodNotFoundException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.warn("Payment method not found [{}]: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                "PAYMENT_METHOD_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI(),
                request.getMethod(),
                traceId
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handle refund not found exceptions
     */
    @ExceptionHandler(RefundNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRefundNotFoundException(
            RefundNotFoundException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.warn("Refund not found [{}]: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                "REFUND_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI(),
                request.getMethod(),
                traceId
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handle refund exceptions
     */
    @ExceptionHandler(RefundException.class)
    public ResponseEntity<ErrorResponse> handleRefundException(
            RefundException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.error("Refund error [{}]: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "REFUND_ERROR",
                ex.getMessage(),
                request.getRequestURI(),
                request.getMethod(),
                traceId
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handle invalid payment method exceptions
     */
    @ExceptionHandler(InvalidPaymentMethodException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPaymentMethodException(
            InvalidPaymentMethodException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.warn("Invalid payment method [{}]: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "INVALID_PAYMENT_METHOD",
                ex.getMessage(),
                request.getRequestURI(),
                request.getMethod(),
                traceId
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handle insufficient funds exceptions
     */
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFundsException(
            InsufficientFundsException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.warn("Insufficient funds [{}]: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "INSUFFICIENT_FUNDS",
                ex.getMessage(),
                request.getRequestURI(),
                request.getMethod(),
                traceId
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handle external service exceptions
     */
    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ErrorResponse> handleExternalServiceException(
            ExternalServiceException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.error("External service error [{}]: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_GATEWAY.value(),
                HttpStatus.BAD_GATEWAY.getReasonPhrase(),
                "EXTERNAL_SERVICE_ERROR",
                ex.getMessage(),
                request.getRequestURI(),
                request.getMethod(),
                traceId
        );

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }

    // ========== COMMON EXCEPTIONS ==========

    /**
     * Handle business exceptions
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.warn("Business exception [{}] for {}: {}", traceId, request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "BUSINESS_ERROR",
                ex.getMessage(),
                request.getRequestURI(),
                request.getMethod(),
                traceId
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handle resource not found exceptions
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.warn("Resource not found [{}] for {}: {}", traceId, request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                "RESOURCE_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI(),
                request.getMethod(),
                traceId
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    // ========== STRIPE SPECIFIC EXCEPTIONS ==========

    /**
     * Handle Stripe API exceptions
     */
    @ExceptionHandler(com.stripe.exception.StripeException.class)
    public ResponseEntity<ErrorResponse> handleStripeException(
            com.stripe.exception.StripeException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.error("Stripe API error [{}]: {}", traceId, ex.getMessage());

        String message = "Payment gateway error: " + ex.getUserMessage();
        if (ex.getUserMessage() == null) {
            message = "Payment gateway error occurred";
        }

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_GATEWAY.value(),
                HttpStatus.BAD_GATEWAY.getReasonPhrase(),
                "STRIPE_API_ERROR",
                message,
                request.getRequestURI(),
                request.getMethod(),
                traceId
        );

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }

    /**
     * Handle Stripe card exceptions
     */
    @ExceptionHandler(com.stripe.exception.CardException.class)
    public ResponseEntity<ErrorResponse> handleStripeCardException(
            com.stripe.exception.CardException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.error("Stripe card error [{}]: {}", traceId, ex.getMessage());

        String message = "Card error: " + ex.getUserMessage();
        if (ex.getUserMessage() == null) {
            message = "Card processing error occurred";
        }

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "CARD_ERROR",
                message,
                request.getRequestURI(),
                request.getMethod(),
                traceId
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handle Stripe invalid request exceptions
     */
    @ExceptionHandler(com.stripe.exception.InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleStripeInvalidRequestException(
            com.stripe.exception.InvalidRequestException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.error("Stripe invalid request [{}]: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "INVALID_PAYMENT_REQUEST",
                "Invalid payment request: " + ex.getUserMessage(),
                request.getRequestURI(),
                request.getMethod(),
                traceId
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handle Stripe authentication exceptions
     */
    @ExceptionHandler(com.stripe.exception.AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleStripeAuthenticationException(
            com.stripe.exception.AuthenticationException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.error("Stripe authentication error [{}]: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "PAYMENT_AUTH_ERROR",
                "Payment service authentication error",
                request.getRequestURI(),
                request.getMethod(),
                traceId
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Handle Stripe rate limit exceptions
     */
    @ExceptionHandler(com.stripe.exception.RateLimitException.class)
    public ResponseEntity<ErrorResponse> handleStripeRateLimitException(
            com.stripe.exception.RateLimitException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.error("Stripe rate limit exceeded [{}]: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "RATE_LIMIT_EXCEEDED",
                "Payment service rate limit exceeded",
                request.getRequestURI(),
                request.getMethod(),
                traceId
        );

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
    }

    // ========== WEBHOOK SPECIFIC EXCEPTIONS ==========

    /**
     * Handle webhook signature verification exceptions
     */
    @ExceptionHandler(com.stripe.exception.SignatureVerificationException.class)
    public ResponseEntity<ErrorResponse> handleWebhookSignatureException(
            com.stripe.exception.SignatureVerificationException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.error("Webhook signature verification failed [{}]: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "WEBHOOK_SIGNATURE_ERROR",
                PaymentConstants.WEBHOOK_SIGNATURE_INVALID,
                request.getRequestURI(),
                request.getMethod(),
                traceId
        );

        return ResponseEntity.badRequest().body(response);
    }

    // ========== SECURITY EXCEPTIONS ==========

    /**
     * Handle authentication exceptions
     */
    @ExceptionHandler({AuthenticationException.class, BadCredentialsException.class})
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.warn("Authentication failed [{}]: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "AUTHENTICATION_ERROR",
                "Authentication failed",
                request.getRequestURI(),
                request.getMethod(),
                traceId
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * Handle access denied exceptions
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.warn("Access denied [{}] for {}: {}", traceId, request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "ACCESS_DENIED",
                "Access denied",
                request.getRequestURI(),
                request.getMethod(),
                traceId
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    // ========== BUSINESS LOGIC EXCEPTIONS ==========

    /**
     * Handle illegal state exceptions
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(
            IllegalStateException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.error("Illegal state error [{}]: {}", traceId, ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "ILLEGAL_STATE",
                ex.getMessage(),
                request.getRequestURI(),
                request.getMethod(),
                traceId
        );

        return ResponseEntity.badRequest().body(response);
    }

    // ========== HTTP EXCEPTIONS ==========

    /**
     * Handle missing request parameter exceptions
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.warn("Missing request parameter [{}] for {}: {}", traceId, request.getRequestURI(), ex.getMessage());

        String message = "Required parameter '" + ex.getParameterName() + "' is missing";
        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "MISSING_PARAMETER",
                message,
                request.getRequestURI(),
                request.getMethod(),
                traceId
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handle method argument type mismatch exceptions
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.warn("Method argument type mismatch [{}] for {}: {}", traceId, request.getRequestURI(), ex.getMessage());

        String message = "Invalid value for parameter '" + ex.getName() + "': " + ex.getValue();
        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "INVALID_PARAMETER_TYPE",
                message,
                request.getRequestURI(),
                request.getMethod(),
                traceId
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handle no handler found exceptions (404)
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFoundException(
            NoHandlerFoundException ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.warn("No handler found [{}] for {}: {}", traceId, request.getRequestURI(), ex.getMessage());

        String message = "Endpoint not found: " + ex.getHttpMethod() + " " + ex.getRequestURL();
        ErrorResponse response = ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                "ENDPOINT_NOT_FOUND",
                message,
                request.getRequestURI(),
                request.getMethod(),
                traceId
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handle all other exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex, HttpServletRequest request) {

        String traceId = generateTraceId();
        log.error("Unexpected error [{}] for {}: {}", traceId, request.getRequestURI(), ex.getMessage(), ex);

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred",
                request.getRequestURI(),
                request.getMethod(),
                traceId
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Generate unique trace ID for error tracking
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

