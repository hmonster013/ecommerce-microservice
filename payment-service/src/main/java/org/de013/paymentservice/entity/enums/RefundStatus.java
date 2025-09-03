package org.de013.paymentservice.entity.enums;

/**
 * Refund status enum aligned with Stripe refund status
 */
public enum RefundStatus {
    /**
     * Refund is pending processing
     */
    PENDING,
    
    /**
     * Refund has been successfully processed
     */
    SUCCEEDED,
    
    /**
     * Refund has failed
     */
    FAILED,
    
    /**
     * Refund has been canceled
     */
    CANCELED,
    
    /**
     * Refund requires action
     */
    REQUIRES_ACTION
}
