package org.de013.paymentservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.de013.common.dto.ApiResponse;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Payment service specific exception handler.
 * This handler provides more detailed error responses for payment-related exceptions.
 * It has higher precedence than the global exception handler.
 */
@Slf4j
@RestControllerAdvice
@Order(1) // Higher precedence than GlobalExceptionHandler
public class PaymentExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentNotFoundException(
            PaymentNotFoundException ex) {
        log.warn("Payment not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(PaymentProcessingException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handlePaymentProcessingException(
            PaymentProcessingException ex) {
        log.error("Payment processing failed: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("message", ex.getMessage());
        if (ex.getPaymentId() != null) {
            errorDetails.put("paymentId", ex.getPaymentId());
        }
        if (ex.getOperation() != null) {
            errorDetails.put("operation", ex.getOperation());
        }
        if (ex.getErrorCode() != null) {
            errorDetails.put("errorCode", ex.getErrorCode());
        }
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Payment processing failed", errorDetails));
    }

    @ExceptionHandler(InvalidPaymentMethodException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleInvalidPaymentMethodException(
            InvalidPaymentMethodException ex) {
        log.warn("Invalid payment method: {}", ex.getMessage());
        
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("message", ex.getMessage());
        if (ex.getPaymentMethodId() != null) {
            errorDetails.put("paymentMethodId", ex.getPaymentMethodId());
        }
        if (ex.getReason() != null) {
            errorDetails.put("reason", ex.getReason());
        }
        if (ex.getValidationCode() != null) {
            errorDetails.put("validationCode", ex.getValidationCode());
        }
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Invalid payment method", errorDetails));
    }

    @ExceptionHandler(StripeException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleStripeException(
            StripeException ex) {
        log.error("Stripe API error: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("message", ex.getMessage());
        if (ex.getStripeErrorCode() != null) {
            errorDetails.put("stripeErrorCode", ex.getStripeErrorCode());
        }
        if (ex.getStripeErrorType() != null) {
            errorDetails.put("stripeErrorType", ex.getStripeErrorType());
        }
        if (ex.getStripeRequestId() != null) {
            errorDetails.put("stripeRequestId", ex.getStripeRequestId());
        }
        if (ex.getPaymentId() != null) {
            errorDetails.put("paymentId", ex.getPaymentId());
        }
        if (ex.getOperation() != null) {
            errorDetails.put("operation", ex.getOperation());
        }
        
        // Map Stripe error types to appropriate HTTP status codes
        HttpStatus status = mapStripeErrorToHttpStatus(ex.getStripeErrorType());
        
        return ResponseEntity.status(status)
                .body(ApiResponse.error("Stripe payment processing failed", errorDetails));
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleInsufficientFundsException(
            InsufficientFundsException ex) {
        log.warn("Insufficient funds: {}", ex.getMessage());
        
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("message", ex.getMessage());
        if (ex.getRequestedAmount() != null) {
            errorDetails.put("requestedAmount", ex.getRequestedAmount());
        }
        if (ex.getAvailableAmount() != null) {
            errorDetails.put("availableAmount", ex.getAvailableAmount());
        }
        if (ex.getCurrency() != null) {
            errorDetails.put("currency", ex.getCurrency());
        }
        if (ex.getPaymentId() != null) {
            errorDetails.put("paymentId", ex.getPaymentId());
        }
        
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(ApiResponse.error("Insufficient funds", errorDetails));
    }

    @ExceptionHandler(RefundException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleRefundException(
            RefundException ex) {
        log.error("Refund operation failed: {}", ex.getMessage(), ex);
        
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("message", ex.getMessage());
        if (ex.getRefundId() != null) {
            errorDetails.put("refundId", ex.getRefundId());
        }
        if (ex.getPaymentId() != null) {
            errorDetails.put("paymentId", ex.getPaymentId());
        }
        if (ex.getOperation() != null) {
            errorDetails.put("operation", ex.getOperation());
        }
        if (ex.getRefundStatus() != null) {
            errorDetails.put("refundStatus", ex.getRefundStatus());
        }
        if (ex.getRefundAmount() != null) {
            errorDetails.put("refundAmount", ex.getRefundAmount());
        }
        if (ex.getCurrency() != null) {
            errorDetails.put("currency", ex.getCurrency());
        }
        if (ex.getErrorCode() != null) {
            errorDetails.put("errorCode", ex.getErrorCode());
        }
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Refund operation failed", errorDetails));
    }

    /**
     * Maps Stripe error types to appropriate HTTP status codes.
     */
    private HttpStatus mapStripeErrorToHttpStatus(String stripeErrorType) {
        if (stripeErrorType == null) {
            return HttpStatus.BAD_REQUEST;
        }
        
        switch (stripeErrorType.toLowerCase()) {
            case "card_error":
                return HttpStatus.PAYMENT_REQUIRED;
            case "rate_limit_error":
                return HttpStatus.TOO_MANY_REQUESTS;
            case "authentication_error":
                return HttpStatus.UNAUTHORIZED;
            case "api_connection_error":
            case "api_error":
                return HttpStatus.BAD_GATEWAY;
            case "validation_error":
                return HttpStatus.BAD_REQUEST;
            case "webhook_error":
                return HttpStatus.BAD_REQUEST;
            default:
                return HttpStatus.BAD_REQUEST;
        }
    }
}
