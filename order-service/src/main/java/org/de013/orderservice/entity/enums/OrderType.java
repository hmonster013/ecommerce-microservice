package org.de013.orderservice.entity.enums;

import lombok.Getter;

/**
 * Order Type Enum
 * 
 * Represents different types of orders that can be placed in the system.
 * Each type may have different processing rules, pricing, and fulfillment requirements.
 * 
 * @author Development Team
 * @version 1.0.0
 */
@Getter
public enum OrderType {
    
    /**
     * Standard order with normal processing and shipping
     */
    STANDARD("STANDARD", "Standard Order", "Normal processing and shipping", 1, false, false),
    
    /**
     * Express order with expedited processing and shipping
     */
    EXPRESS("EXPRESS", "Express Order", "Expedited processing and shipping", 2, true, false),
    
    /**
     * Gift order with special packaging and messaging
     */
    GIFT("GIFT", "Gift Order", "Special gift packaging and messaging", 3, false, true),
    
    /**
     * Subscription order for recurring deliveries
     */
    SUBSCRIPTION("SUBSCRIPTION", "Subscription Order", "Recurring subscription delivery", 4, false, false),
    
    /**
     * Pre-order for items not yet available
     */
    PRE_ORDER("PRE_ORDER", "Pre-Order", "Order for future availability items", 5, false, false),
    
    /**
     * Back-order for out-of-stock items
     */
    BACK_ORDER("BACK_ORDER", "Back Order", "Order for currently out-of-stock items", 6, false, false),
    
    /**
     * Wholesale order for business customers
     */
    WHOLESALE("WHOLESALE", "Wholesale Order", "Bulk order for business customers", 7, false, false),
    
    /**
     * Drop-ship order fulfilled directly by supplier
     */
    DROP_SHIP("DROP_SHIP", "Drop Ship Order", "Fulfilled directly by supplier", 8, false, false),
    
    /**
     * Digital order for digital products/services
     */
    DIGITAL("DIGITAL", "Digital Order", "Digital products or services", 9, true, false),
    
    /**
     * Sample order for product samples
     */
    SAMPLE("SAMPLE", "Sample Order", "Product samples for evaluation", 10, false, false),
    
    /**
     * Return merchandise authorization order
     */
    RMA("RMA", "Return Order", "Return merchandise authorization", 11, false, false),
    
    /**
     * Exchange order for product exchanges
     */
    EXCHANGE("EXCHANGE", "Exchange Order", "Product exchange order", 12, false, false);
    
    private final String code;
    private final String displayName;
    private final String description;
    private final int priority;
    private final boolean expedited;
    private final boolean requiresSpecialHandling;
    
    OrderType(String code, String displayName, String description, int priority, 
              boolean expedited, boolean requiresSpecialHandling) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.priority = priority;
        this.expedited = expedited;
        this.requiresSpecialHandling = requiresSpecialHandling;
    }
    
    /**
     * Get OrderType by code
     * 
     * @param code the order type code
     * @return OrderType or null if not found
     */
    public static OrderType fromCode(String code) {
        if (code == null) {
            return null;
        }
        
        for (OrderType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
    
    /**
     * Check if this order type requires immediate processing
     * 
     * @return true if requires immediate processing
     */
    public boolean requiresImmediateProcessing() {
        return this.expedited || this == EXPRESS || this == DIGITAL;
    }
    
    /**
     * Check if this order type supports inventory reservation
     * 
     * @return true if supports inventory reservation
     */
    public boolean supportsInventoryReservation() {
        return this != DIGITAL && this != PRE_ORDER && this != BACK_ORDER;
    }
    
    /**
     * Check if this order type requires payment upfront
     * 
     * @return true if requires upfront payment
     */
    public boolean requiresUpfrontPayment() {
        return this != WHOLESALE && this != RMA && this != EXCHANGE;
    }
    
    /**
     * Check if this order type supports partial fulfillment
     * 
     * @return true if supports partial fulfillment
     */
    public boolean supportsPartialFulfillment() {
        return this == STANDARD || this == WHOLESALE || this == BACK_ORDER;
    }
    
    /**
     * Get the default processing time in hours for this order type
     * 
     * @return processing time in hours
     */
    public int getDefaultProcessingTimeHours() {
        return switch (this) {
            case EXPRESS, DIGITAL -> 2;
            case STANDARD, GIFT -> 24;
            case SUBSCRIPTION -> 12;
            case WHOLESALE -> 48;
            case DROP_SHIP -> 72;
            case PRE_ORDER, BACK_ORDER -> 168; // 1 week
            case SAMPLE -> 6;
            case RMA, EXCHANGE -> 24;
        };
    }
    
    /**
     * Get the shipping priority for this order type
     * 
     * @return shipping priority (1 = highest, 10 = lowest)
     */
    public int getShippingPriority() {
        return switch (this) {
            case EXPRESS -> 1;
            case GIFT -> 2;
            case STANDARD -> 3;
            case SUBSCRIPTION -> 4;
            case DIGITAL -> 1; // No shipping but high priority for processing
            case WHOLESALE -> 5;
            case SAMPLE -> 3;
            case DROP_SHIP -> 6;
            case PRE_ORDER, BACK_ORDER -> 7;
            case RMA, EXCHANGE -> 4;
        };
    }
    
    /**
     * Check if this order type is eligible for free shipping promotions
     * 
     * @return true if eligible for free shipping
     */
    public boolean isEligibleForFreeShipping() {
        return this == STANDARD || this == GIFT || this == SUBSCRIPTION;
    }
    
    /**
     * Check if this order type requires special documentation
     * 
     * @return true if requires special documentation
     */
    public boolean requiresSpecialDocumentation() {
        return this == WHOLESALE || this == RMA || this == EXCHANGE || this == DROP_SHIP;
    }
}
