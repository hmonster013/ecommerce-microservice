package org.de013.paymentservice.entity.enums;

/**
 * Transaction types for payment operations
 */
public enum TransactionType {
    /**
     * Payment charge transaction
     */
    CHARGE,
    
    /**
     * Payment authorization (hold funds)
     */
    AUTHORIZATION,
    
    /**
     * Capture authorized payment
     */
    CAPTURE,
    
    /**
     * Refund transaction
     */
    REFUND,
    
    /**
     * Partial refund transaction
     */
    PARTIAL_REFUND,
    
    /**
     * Payment cancellation
     */
    CANCELLATION,
    
    /**
     * Payment failure
     */
    FAILURE
}
