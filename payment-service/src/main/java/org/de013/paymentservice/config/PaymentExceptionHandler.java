package org.de013.paymentservice.config;

import lombok.extern.slf4j.Slf4j;
import org.de013.common.dto.ApiResponse;
import org.de013.common.exception.GlobalExceptionHandler;
import org.de013.paymentservice.constant.PaymentConstants;
import org.de013.paymentservice.exception.PaymentGatewayException;
import org.de013.paymentservice.exception.PaymentNotFoundException;
import org.de013.paymentservice.exception.PaymentProcessingException;
import org.de013.paymentservice.exception.PaymentMethodNotFoundException;
import org.de013.paymentservice.exception.RefundNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Payment Service specific exception handler
 * Extends common GlobalExceptionHandler for payment-specific exceptions
 */
@RestControllerAdvice
@Slf4j
public class PaymentExceptionHandler extends GlobalExceptionHandler {

    // ========== PAYMENT SPECIFIC EXCEPTIONS ==========

    @ExceptionHandler(PaymentProcessingException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentProcessingException(
            PaymentProcessingException ex, WebRequest request) {
        log.error("Payment processing error: {}", ex.getMessage());
        
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ex.getMessage(), "PAYMENT_PROCESSING_ERROR", 
                        request.getDescription(false)));
    }

    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentGatewayException(
            PaymentGatewayException ex, WebRequest request) {
        log.error("Payment gateway error: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ex.getMessage(), "PAYMENT_GATEWAY_ERROR", 
                        request.getDescription(false)));
    }

    // ========== STRIPE SPECIFIC EXCEPTIONS ==========

    @ExceptionHandler(com.stripe.exception.StripeException.class)
    public ResponseEntity<ApiResponse<Void>> handleStripeException(
            com.stripe.exception.StripeException ex, WebRequest request) {
        log.error("Stripe API error: {}", ex.getMessage());
        
        String message = "Payment gateway error: " + ex.getUserMessage();
        if (ex.getUserMessage() == null) {
            message = "Payment gateway error occurred";
        }
        
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(message, "STRIPE_API_ERROR", 
                        request.getDescription(false)));
    }

    @ExceptionHandler(com.stripe.exception.CardException.class)
    public ResponseEntity<ApiResponse<Void>> handleStripeCardException(
            com.stripe.exception.CardException ex, WebRequest request) {
        log.error("Stripe card error: {}", ex.getMessage());
        
        String message = "Card error: " + ex.getUserMessage();
        if (ex.getUserMessage() == null) {
            message = "Card processing error occurred";
        }
        
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(message, "CARD_ERROR", 
                        request.getDescription(false)));
    }

    @ExceptionHandler(com.stripe.exception.InvalidRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleStripeInvalidRequestException(
            com.stripe.exception.InvalidRequestException ex, WebRequest request) {
        log.error("Stripe invalid request: {}", ex.getMessage());
        
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("Invalid payment request: " + ex.getUserMessage(), 
                        "INVALID_PAYMENT_REQUEST", request.getDescription(false)));
    }

    @ExceptionHandler(com.stripe.exception.AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleStripeAuthenticationException(
            com.stripe.exception.AuthenticationException ex, WebRequest request) {
        log.error("Stripe authentication error: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Payment service authentication error", 
                        "PAYMENT_AUTH_ERROR", request.getDescription(false)));
    }

    @ExceptionHandler(com.stripe.exception.RateLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handleStripeRateLimitException(
            com.stripe.exception.RateLimitException ex, WebRequest request) {
        log.error("Stripe rate limit exceeded: {}", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error("Payment service rate limit exceeded", 
                        "RATE_LIMIT_EXCEEDED", request.getDescription(false)));
    }

    // ========== WEBHOOK SPECIFIC EXCEPTIONS ==========

    @ExceptionHandler(com.stripe.exception.SignatureVerificationException.class)
    public ResponseEntity<ApiResponse<Void>> handleWebhookSignatureException(
            com.stripe.exception.SignatureVerificationException ex, WebRequest request) {
        log.error("Webhook signature verification failed: {}", ex.getMessage());
        
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(PaymentConstants.WEBHOOK_SIGNATURE_INVALID, 
                        "WEBHOOK_SIGNATURE_ERROR", request.getDescription(false)));
    }

    // ========== BUSINESS LOGIC EXCEPTIONS ==========

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(
            IllegalStateException ex, WebRequest request) {
        log.error("Illegal state error: {}", ex.getMessage());
        
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ex.getMessage(), "ILLEGAL_STATE", 
                        request.getDescription(false)));
    }
}
