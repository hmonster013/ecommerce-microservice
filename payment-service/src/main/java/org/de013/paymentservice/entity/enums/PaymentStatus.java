package org.de013.paymentservice.entity.enums;

/**
 * Payment status enum aligned with Stripe PaymentIntent status
 */
public enum PaymentStatus {
    /**
     * Payment is being processed
     */
    PENDING,
    
    /**
     * Payment requires additional action (3D Secure, etc.)
     */
    REQUIRES_ACTION,
    
    /**
     * Payment requires confirmation
     */
    REQUIRES_CONFIRMATION,
    
    /**
     * Payment requires payment method
     */
    REQUIRES_PAYMENT_METHOD,
    
    /**
     * Payment has been successfully processed
     */
    SUCCEEDED,
    
    /**
     * Payment has been canceled
     */
    CANCELED,
    
    /**
     * Payment has failed
     */
    FAILED,
    
    /**
     * Payment is processing (async)
     */
    PROCESSING
}
