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
     * Refund is being processed
     */
    PROCESSING,

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
     * Refund has been rejected
     */
    REJECTED,

    /**
     * Refund requires action
     */
    REQUIRES_ACTION
}
