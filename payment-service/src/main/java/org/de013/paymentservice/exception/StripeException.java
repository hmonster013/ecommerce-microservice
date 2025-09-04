package org.de013.paymentservice.exception;

/**
 * Exception thrown when Stripe API operations fail.
 * This wraps Stripe-specific errors and provides additional context.
 */
public class StripeException extends PaymentProcessingException {
    
    private final String stripeErrorCode;
    private final String stripeErrorType;
    private final String stripeRequestId;
    private final Integer httpStatusCode;
    
    public StripeException(String message) {
        super(message);
        this.stripeErrorCode = null;
        this.stripeErrorType = null;
        this.stripeRequestId = null;
        this.httpStatusCode = null;
    }
    
    public StripeException(String message, Throwable cause) {
        super(message, cause);
        this.stripeErrorCode = null;
        this.stripeErrorType = null;
        this.stripeRequestId = null;
        this.httpStatusCode = null;
    }
    
    public StripeException(String message, String stripeErrorCode, String stripeErrorType) {
        super(message);
        this.stripeErrorCode = stripeErrorCode;
        this.stripeErrorType = stripeErrorType;
        this.stripeRequestId = null;
        this.httpStatusCode = null;
    }
    
    public StripeException(String message, String stripeErrorCode, String stripeErrorType, 
                          String stripeRequestId, Integer httpStatusCode) {
        super(message);
        this.stripeErrorCode = stripeErrorCode;
        this.stripeErrorType = stripeErrorType;
        this.stripeRequestId = stripeRequestId;
        this.httpStatusCode = httpStatusCode;
    }
    
    public StripeException(String message, String paymentId, String operation, 
                          String stripeErrorCode, String stripeErrorType, Throwable cause) {
        super(message, paymentId, operation, cause);
        this.stripeErrorCode = stripeErrorCode;
        this.stripeErrorType = stripeErrorType;
        this.stripeRequestId = null;
        this.httpStatusCode = null;
    }
    
    // Getters
    public String getStripeErrorCode() {
        return stripeErrorCode;
    }
    
    public String getStripeErrorType() {
        return stripeErrorType;
    }
    
    public String getStripeRequestId() {
        return stripeRequestId;
    }
    
    public Integer getHttpStatusCode() {
        return httpStatusCode;
    }
    
    // Static factory methods for common Stripe errors
    public static StripeException cardDeclined(String paymentId, String declineCode) {
        return new StripeException(
            "Card was declined: " + declineCode,
            paymentId,
            "PROCESS",
            "card_declined",
            "card_error",
            null
        );
    }
    
    public static StripeException insufficientFunds(String paymentId) {
        return new StripeException(
            "Insufficient funds on the card",
            paymentId,
            "PROCESS",
            "insufficient_funds",
            "card_error",
            null
        );
    }
    
    public static StripeException expiredCard(String paymentId) {
        return new StripeException(
            "Card has expired",
            paymentId,
            "PROCESS",
            "expired_card",
            "card_error",
            null
        );
    }
    
    public static StripeException invalidCvc(String paymentId) {
        return new StripeException(
            "Invalid CVC code",
            paymentId,
            "PROCESS",
            "incorrect_cvc",
            "card_error",
            null
        );
    }
    
    public static StripeException processingError(String paymentId, String message) {
        return new StripeException(
            "Processing error: " + message,
            paymentId,
            "PROCESS",
            "processing_error",
            "api_error",
            null
        );
    }
    
    public static StripeException rateLimitExceeded() {
        return new StripeException(
            "Rate limit exceeded",
            "rate_limit",
            "rate_limit_error"
        );
    }
    
    public static StripeException authenticationFailed() {
        return new StripeException(
            "Authentication with Stripe failed",
            "authentication_required",
            "authentication_error"
        );
    }
    
    public static StripeException webhookSignatureInvalid() {
        return new StripeException(
            "Invalid webhook signature",
            "invalid_signature",
            "webhook_error"
        );
    }
    
    /**
     * Creates a StripeException from a Stripe API exception.
     * This method should be used to wrap actual Stripe exceptions.
     */
    public static StripeException fromStripeError(com.stripe.exception.StripeException stripeEx) {
        return new StripeException(
            stripeEx.getMessage(),
            stripeEx.getCode(),
            stripeEx.getClass().getSimpleName(),
            stripeEx.getRequestId(),
            stripeEx.getStatusCode()
        );
    }
}
