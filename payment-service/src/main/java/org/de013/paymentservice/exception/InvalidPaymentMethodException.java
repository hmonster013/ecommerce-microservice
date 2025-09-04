package org.de013.paymentservice.exception;

import org.de013.common.exception.BusinessException;

/**
 * Exception thrown when a payment method is invalid or cannot be used.
 * This includes expired cards, inactive payment methods, or unsupported payment types.
 */
public class InvalidPaymentMethodException extends BusinessException {
    
    private final String paymentMethodId;
    private final String reason;
    private final String validationCode;
    
    public InvalidPaymentMethodException(String message) {
        super(message);
        this.paymentMethodId = null;
        this.reason = null;
        this.validationCode = null;
    }
    
    public InvalidPaymentMethodException(String message, Throwable cause) {
        super(message, cause);
        this.paymentMethodId = null;
        this.reason = null;
        this.validationCode = null;
    }
    
    public InvalidPaymentMethodException(String message, String paymentMethodId, String reason) {
        super(message);
        this.paymentMethodId = paymentMethodId;
        this.reason = reason;
        this.validationCode = null;
    }
    
    public InvalidPaymentMethodException(String message, String paymentMethodId, String reason, String validationCode) {
        super(message);
        this.paymentMethodId = paymentMethodId;
        this.reason = reason;
        this.validationCode = validationCode;
    }
    
    // Getters
    public String getPaymentMethodId() {
        return paymentMethodId;
    }
    
    public String getReason() {
        return reason;
    }
    
    public String getValidationCode() {
        return validationCode;
    }
    
    // Static factory methods for common scenarios
    public static InvalidPaymentMethodException expired(String paymentMethodId) {
        return new InvalidPaymentMethodException(
            "Payment method has expired", 
            paymentMethodId, 
            "EXPIRED",
            "PAYMENT_METHOD_EXPIRED"
        );
    }
    
    public static InvalidPaymentMethodException inactive(String paymentMethodId) {
        return new InvalidPaymentMethodException(
            "Payment method is inactive", 
            paymentMethodId, 
            "INACTIVE",
            "PAYMENT_METHOD_INACTIVE"
        );
    }
    
    public static InvalidPaymentMethodException notFound(String paymentMethodId) {
        return new InvalidPaymentMethodException(
            "Payment method not found", 
            paymentMethodId, 
            "NOT_FOUND",
            "PAYMENT_METHOD_NOT_FOUND"
        );
    }
    
    public static InvalidPaymentMethodException unsupportedType(String paymentMethodId, String type) {
        return new InvalidPaymentMethodException(
            "Unsupported payment method type: " + type, 
            paymentMethodId, 
            "UNSUPPORTED_TYPE",
            "PAYMENT_METHOD_UNSUPPORTED"
        );
    }
    
    public static InvalidPaymentMethodException insufficientFunds(String paymentMethodId) {
        return new InvalidPaymentMethodException(
            "Insufficient funds on payment method", 
            paymentMethodId, 
            "INSUFFICIENT_FUNDS",
            "PAYMENT_METHOD_INSUFFICIENT_FUNDS"
        );
    }
    
    public static InvalidPaymentMethodException blocked(String paymentMethodId, String reason) {
        return new InvalidPaymentMethodException(
            "Payment method is blocked: " + reason, 
            paymentMethodId, 
            "BLOCKED",
            "PAYMENT_METHOD_BLOCKED"
        );
    }
    
    public static InvalidPaymentMethodException validationFailed(String paymentMethodId, String details) {
        return new InvalidPaymentMethodException(
            "Payment method validation failed: " + details, 
            paymentMethodId, 
            "VALIDATION_FAILED",
            "PAYMENT_METHOD_VALIDATION_FAILED"
        );
    }
}
