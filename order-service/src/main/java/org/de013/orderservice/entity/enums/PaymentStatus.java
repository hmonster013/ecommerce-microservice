package org.de013.orderservice.entity.enums;

import lombok.Getter;

/**
 * Payment Status Enum
 * 
 * Represents the various states a payment can be in throughout its lifecycle.
 * This enum follows payment processing standards and supports various payment flows.
 * 
 * @author Development Team
 * @version 1.0.0
 */
@Getter
public enum PaymentStatus {
    
    /**
     * Payment is pending initiation
     */
    PENDING("PENDING", "Payment is pending", 1, false, false, false),
    
    /**
     * Payment has been initiated but not yet processed
     */
    INITIATED("INITIATED", "Payment has been initiated", 2, false, false, false),
    
    /**
     * Payment is being processed by payment gateway
     */
    PROCESSING("PROCESSING", "Payment is being processed", 3, false, false, false),
    
    /**
     * Payment has been authorized but not captured
     */
    AUTHORIZED("AUTHORIZED", "Payment authorized", 4, true, false, false),
    
    /**
     * Payment has been captured/charged successfully
     */
    CAPTURED("CAPTURED", "Payment captured successfully", 5, true, true, false),
    
    /**
     * Payment has been settled with the merchant
     */
    SETTLED("SETTLED", "Payment settled", 6, true, true, false),
    
    /**
     * Payment has failed due to various reasons
     */
    FAILED("FAILED", "Payment failed", -1, false, false, true),
    
    /**
     * Payment has been declined by bank/card issuer
     */
    DECLINED("DECLINED", "Payment declined", -2, false, false, true),
    
    /**
     * Payment has been cancelled by customer or system
     */
    CANCELLED("CANCELLED", "Payment cancelled", -3, false, false, true),
    
    /**
     * Payment has expired (authorization or payment window)
     */
    EXPIRED("EXPIRED", "Payment expired", -4, false, false, true),
    
    /**
     * Payment has been refunded partially
     */
    PARTIALLY_REFUNDED("PARTIALLY_REFUNDED", "Payment partially refunded", 7, true, true, false),
    
    /**
     * Payment has been fully refunded
     */
    REFUNDED("REFUNDED", "Payment fully refunded", 8, false, false, true),
    
    /**
     * Payment is being refunded (in progress)
     */
    REFUNDING("REFUNDING", "Payment refund in progress", 9, true, true, false),
    
    /**
     * Payment refund has failed
     */
    REFUND_FAILED("REFUND_FAILED", "Payment refund failed", -5, true, true, true),
    
    /**
     * Payment is under review for fraud/compliance
     */
    UNDER_REVIEW("UNDER_REVIEW", "Payment under review", 10, false, false, false),
    
    /**
     * Payment has been disputed by customer
     */
    DISPUTED("DISPUTED", "Payment disputed", 11, true, true, true),
    
    /**
     * Payment dispute has been resolved in favor of merchant
     */
    DISPUTE_WON("DISPUTE_WON", "Dispute resolved - won", 12, true, true, false),
    
    /**
     * Payment dispute has been resolved in favor of customer
     */
    DISPUTE_LOST("DISPUTE_LOST", "Dispute resolved - lost", 13, false, false, true),
    
    /**
     * Payment has been charged back by bank
     */
    CHARGEBACK("CHARGEBACK", "Payment charged back", 14, false, false, true),
    
    /**
     * Payment is on hold due to risk assessment
     */
    ON_HOLD("ON_HOLD", "Payment on hold", 0, false, false, false);
    
    private final String code;
    private final String description;
    private final int sequence;
    private final boolean fundsReserved;
    private final boolean fundsTransferred;
    private final boolean isFinal;
    
    PaymentStatus(String code, String description, int sequence, 
                  boolean fundsReserved, boolean fundsTransferred, boolean isFinal) {
        this.code = code;
        this.description = description;
        this.sequence = sequence;
        this.fundsReserved = fundsReserved;
        this.fundsTransferred = fundsTransferred;
        this.isFinal = isFinal;
    }
    
    /**
     * Get PaymentStatus by code
     * 
     * @param code the payment status code
     * @return PaymentStatus or null if not found
     */
    public static PaymentStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        
        for (PaymentStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }
    
    /**
     * Check if this status can transition to the target status
     * 
     * @param targetStatus the target status to transition to
     * @return true if transition is allowed
     */
    public boolean canTransitionTo(PaymentStatus targetStatus) {
        if (targetStatus == null) {
            return false;
        }
        
        // Allow transition to same status (idempotent)
        if (this == targetStatus) {
            return true;
        }
        
        // No transitions from final states except for disputes
        if (this.isFinal && targetStatus != DISPUTED && targetStatus != CHARGEBACK) {
            return false;
        }
        
        // Specific transition rules
        return switch (this) {
            case PENDING -> targetStatus == INITIATED || targetStatus == CANCELLED || targetStatus == EXPIRED;
            case INITIATED -> targetStatus == PROCESSING || targetStatus == FAILED || targetStatus == CANCELLED;
            case PROCESSING -> targetStatus == AUTHORIZED || targetStatus == CAPTURED || 
                              targetStatus == FAILED || targetStatus == DECLINED || targetStatus == UNDER_REVIEW;
            case AUTHORIZED -> targetStatus == CAPTURED || targetStatus == CANCELLED || 
                              targetStatus == EXPIRED || targetStatus == UNDER_REVIEW;
            case CAPTURED -> targetStatus == SETTLED || targetStatus == REFUNDING || 
                            targetStatus == PARTIALLY_REFUNDED || targetStatus == DISPUTED;
            case SETTLED -> targetStatus == REFUNDING || targetStatus == PARTIALLY_REFUNDED || 
                           targetStatus == DISPUTED || targetStatus == CHARGEBACK;
            case PARTIALLY_REFUNDED -> targetStatus == REFUNDING || targetStatus == REFUNDED || 
                                      targetStatus == DISPUTED;
            case REFUNDING -> targetStatus == REFUNDED || targetStatus == REFUND_FAILED;
            case UNDER_REVIEW -> targetStatus == AUTHORIZED || targetStatus == CAPTURED || 
                                targetStatus == FAILED || targetStatus == ON_HOLD;
            case DISPUTED -> targetStatus == DISPUTE_WON || targetStatus == DISPUTE_LOST;
            case ON_HOLD -> targetStatus == PROCESSING || targetStatus == CANCELLED;
            default -> false;
        };
    }
    
    /**
     * Check if payment is successful and funds are available
     * 
     * @return true if payment is successful
     */
    public boolean isSuccessful() {
        return this == AUTHORIZED || this == CAPTURED || this == SETTLED || 
               this == PARTIALLY_REFUNDED || this == DISPUTE_WON;
    }
    
    /**
     * Check if payment has failed or been rejected
     * 
     * @return true if payment failed
     */
    public boolean isFailed() {
        return this == FAILED || this == DECLINED || this == CANCELLED || 
               this == EXPIRED || this == REFUND_FAILED || this == DISPUTE_LOST || 
               this == CHARGEBACK;
    }
    
    /**
     * Check if payment is in a pending state
     * 
     * @return true if payment is pending
     */
    public boolean isPending() {
        return this == PENDING || this == INITIATED || this == PROCESSING || 
               this == UNDER_REVIEW || this == ON_HOLD || this == REFUNDING;
    }
    
    /**
     * Check if payment can be refunded
     * 
     * @return true if refund is possible
     */
    public boolean canBeRefunded() {
        return this == CAPTURED || this == SETTLED || this == PARTIALLY_REFUNDED;
    }
    
    /**
     * Check if payment can be cancelled
     * 
     * @return true if cancellation is possible
     */
    public boolean canBeCancelled() {
        return this == PENDING || this == INITIATED || this == AUTHORIZED || this == ON_HOLD;
    }
    
    /**
     * Get the risk level associated with this payment status
     * 
     * @return risk level (1 = low, 5 = high)
     */
    public int getRiskLevel() {
        return switch (this) {
            case SETTLED, CAPTURED -> 1;
            case AUTHORIZED, PARTIALLY_REFUNDED -> 2;
            case PROCESSING, REFUNDING -> 3;
            case UNDER_REVIEW, DISPUTED, ON_HOLD -> 4;
            case FAILED, DECLINED, CHARGEBACK, DISPUTE_LOST -> 5;
            default -> 3;
        };
    }
}
