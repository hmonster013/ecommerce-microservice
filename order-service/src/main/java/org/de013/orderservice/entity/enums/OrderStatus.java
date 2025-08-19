package org.de013.orderservice.entity.enums;

import lombok.Getter;

/**
 * Order Status Enum
 * 
 * Represents the various states an order can be in throughout its lifecycle.
 * This enum follows a logical progression from order creation to completion.
 * 
 * @author Development Team
 * @version 1.0.0
 */
@Getter
public enum OrderStatus {
    
    /**
     * Order has been created but not yet confirmed by the customer
     */
    PENDING("PENDING", "Order is pending customer confirmation", 1),
    
    /**
     * Order has been confirmed by the customer and is awaiting payment
     */
    CONFIRMED("CONFIRMED", "Order confirmed, awaiting payment", 2),
    
    /**
     * Payment has been authorized but not yet captured
     */
    PAYMENT_AUTHORIZED("PAYMENT_AUTHORIZED", "Payment authorized", 3),
    
    /**
     * Payment has been successfully processed
     */
    PAID("PAID", "Payment completed successfully", 4),
    
    /**
     * Order is being processed (inventory allocation, preparation)
     */
    PROCESSING("PROCESSING", "Order is being processed", 5),
    
    /**
     * Order items are being prepared for shipment
     */
    PREPARING("PREPARING", "Order is being prepared for shipment", 6),
    
    /**
     * Order has been shipped to the customer
     */
    SHIPPED("SHIPPED", "Order has been shipped", 7),
    
    /**
     * Order is out for delivery
     */
    OUT_FOR_DELIVERY("OUT_FOR_DELIVERY", "Order is out for delivery", 8),
    
    /**
     * Order has been successfully delivered to the customer
     */
    DELIVERED("DELIVERED", "Order has been delivered", 9),
    
    /**
     * Order has been completed (customer confirmed receipt)
     */
    COMPLETED("COMPLETED", "Order completed successfully", 10),
    
    /**
     * Order has been cancelled by customer or system
     */
    CANCELLED("CANCELLED", "Order has been cancelled", -1),
    
    /**
     * Order has been refunded
     */
    REFUNDED("REFUNDED", "Order has been refunded", -2),
    
    /**
     * Order has failed due to payment or other issues
     */
    FAILED("FAILED", "Order processing failed", -3),
    
    /**
     * Order is on hold due to various reasons
     */
    ON_HOLD("ON_HOLD", "Order is on hold", 0),
    
    /**
     * Order has been partially shipped (for multi-item orders)
     */
    PARTIALLY_SHIPPED("PARTIALLY_SHIPPED", "Order partially shipped", 6),
    
    /**
     * Order has been partially delivered
     */
    PARTIALLY_DELIVERED("PARTIALLY_DELIVERED", "Order partially delivered", 8),
    
    /**
     * Order is being returned by customer
     */
    RETURNING("RETURNING", "Order is being returned", -4),
    
    /**
     * Order has been returned and processed
     */
    RETURNED("RETURNED", "Order has been returned", -5);
    
    private final String code;
    private final String description;
    private final int sequence;
    
    OrderStatus(String code, String description, int sequence) {
        this.code = code;
        this.description = description;
        this.sequence = sequence;
    }
    
    /**
     * Get OrderStatus by code
     * 
     * @param code the status code
     * @return OrderStatus or null if not found
     */
    public static OrderStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        
        for (OrderStatus status : values()) {
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
    public boolean canTransitionTo(OrderStatus targetStatus) {
        if (targetStatus == null) {
            return false;
        }
        
        // Allow transition to same status (idempotent)
        if (this == targetStatus) {
            return true;
        }
        
        // Allow transition to CANCELLED or ON_HOLD from most states
        if (targetStatus == CANCELLED || targetStatus == ON_HOLD) {
            return this != COMPLETED && this != DELIVERED && this != REFUNDED && this != RETURNED;
        }
        
        // Allow transition to FAILED from early states
        if (targetStatus == FAILED) {
            return this.sequence <= PROCESSING.sequence;
        }
        
        // Allow return process from delivered/completed states
        if (targetStatus == RETURNING) {
            return this == DELIVERED || this == COMPLETED;
        }
        
        if (targetStatus == RETURNED) {
            return this == RETURNING;
        }
        
        // Allow refund from various states
        if (targetStatus == REFUNDED) {
            return this == PAID || this == PROCESSING || this == PREPARING || 
                   this == CANCELLED || this == RETURNED;
        }
        
        // Normal forward progression
        if (this.sequence > 0 && targetStatus.sequence > 0) {
            return targetStatus.sequence == this.sequence + 1 || 
                   (targetStatus.sequence > this.sequence && targetStatus.sequence <= 10);
        }
        
        return false;
    }
    
    /**
     * Check if this is a final status (no further transitions allowed)
     * 
     * @return true if this is a final status
     */
    public boolean isFinal() {
        return this == COMPLETED || this == CANCELLED || this == REFUNDED || 
               this == FAILED || this == RETURNED;
    }
    
    /**
     * Check if this status indicates the order is active
     * 
     * @return true if order is active
     */
    public boolean isActive() {
        return this.sequence > 0 && this.sequence <= 10;
    }
    
    /**
     * Check if this status indicates a problem state
     * 
     * @return true if this is a problem state
     */
    public boolean isProblemState() {
        return this.sequence < 0 || this == ON_HOLD;
    }
}
