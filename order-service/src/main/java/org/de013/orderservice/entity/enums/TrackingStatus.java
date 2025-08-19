package org.de013.orderservice.entity.enums;

import lombok.Getter;

/**
 * Tracking Status Enum
 * 
 * Represents the various states in the order tracking and fulfillment process.
 * This enum provides detailed tracking information for order shipments.
 * 
 * @author Development Team
 * @version 1.0.0
 */
@Getter
public enum TrackingStatus {
    
    /**
     * Order has been placed and is being processed
     */
    ORDER_PLACED("ORDER_PLACED", "Order Placed", "Order has been placed and confirmed", 1, false, false),
    
    /**
     * Order is being processed and prepared
     */
    PROCESSING("PROCESSING", "Processing", "Order is being processed", 2, false, false),
    
    /**
     * Items are being picked from inventory
     */
    PICKING("PICKING", "Picking Items", "Items are being picked from warehouse", 3, false, false),
    
    /**
     * Items are being packed for shipment
     */
    PACKING("PACKING", "Packing", "Items are being packed for shipment", 4, false, false),
    
    /**
     * Package is ready for shipment
     */
    READY_FOR_SHIPMENT("READY_FOR_SHIPMENT", "Ready for Shipment", "Package is ready to be shipped", 5, false, false),
    
    /**
     * Package has been shipped
     */
    SHIPPED("SHIPPED", "Shipped", "Package has been shipped", 6, true, false),
    
    /**
     * Package is in transit to destination
     */
    IN_TRANSIT("IN_TRANSIT", "In Transit", "Package is in transit", 7, true, false),
    
    /**
     * Package has arrived at local facility
     */
    ARRIVED_AT_FACILITY("ARRIVED_AT_FACILITY", "Arrived at Facility", "Package arrived at local facility", 8, true, false),
    
    /**
     * Package is out for delivery
     */
    OUT_FOR_DELIVERY("OUT_FOR_DELIVERY", "Out for Delivery", "Package is out for delivery", 9, true, false),
    
    /**
     * Delivery attempt was made but failed
     */
    DELIVERY_ATTEMPTED("DELIVERY_ATTEMPTED", "Delivery Attempted", "Delivery was attempted but failed", 10, true, false),
    
    /**
     * Package has been delivered successfully
     */
    DELIVERED("DELIVERED", "Delivered", "Package has been delivered", 11, true, true),
    
    /**
     * Package delivery has been confirmed by recipient
     */
    DELIVERY_CONFIRMED("DELIVERY_CONFIRMED", "Delivery Confirmed", "Delivery confirmed by recipient", 12, true, true),
    
    /**
     * Package is being returned to sender
     */
    RETURNING("RETURNING", "Returning", "Package is being returned", -1, true, false),
    
    /**
     * Package has been returned to sender
     */
    RETURNED("RETURNED", "Returned", "Package has been returned to sender", -2, false, true),
    
    /**
     * Package is lost in transit
     */
    LOST("LOST", "Lost", "Package is lost in transit", -3, false, true),
    
    /**
     * Package has been damaged during transit
     */
    DAMAGED("DAMAGED", "Damaged", "Package was damaged during transit", -4, false, true),
    
    /**
     * Shipment has been cancelled
     */
    CANCELLED("CANCELLED", "Cancelled", "Shipment has been cancelled", -5, false, true),
    
    /**
     * Shipment is on hold due to various reasons
     */
    ON_HOLD("ON_HOLD", "On Hold", "Shipment is on hold", 0, false, false),
    
    /**
     * Package is at pickup location waiting for customer
     */
    READY_FOR_PICKUP("READY_FOR_PICKUP", "Ready for Pickup", "Package is ready for customer pickup", 13, true, false),
    
    /**
     * Package has been picked up by customer
     */
    PICKED_UP("PICKED_UP", "Picked Up", "Package has been picked up by customer", 14, true, true),
    
    /**
     * Delivery requires signature
     */
    SIGNATURE_REQUIRED("SIGNATURE_REQUIRED", "Signature Required", "Delivery requires recipient signature", 15, true, false),
    
    /**
     * Package is in customs for international shipments
     */
    IN_CUSTOMS("IN_CUSTOMS", "In Customs", "Package is being processed by customs", 16, true, false),
    
    /**
     * Customs clearance completed
     */
    CUSTOMS_CLEARED("CUSTOMS_CLEARED", "Customs Cleared", "Package cleared customs", 17, true, false),
    
    /**
     * Package delivery is rescheduled
     */
    DELIVERY_RESCHEDULED("DELIVERY_RESCHEDULED", "Delivery Rescheduled", "Delivery has been rescheduled", 18, true, false);
    
    private final String code;
    private final String displayName;
    private final String description;
    private final int sequence;
    private final boolean inTransit;
    private final boolean isFinal;
    
    TrackingStatus(String code, String displayName, String description, 
                   int sequence, boolean inTransit, boolean isFinal) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.sequence = sequence;
        this.inTransit = inTransit;
        this.isFinal = isFinal;
    }
    
    /**
     * Get TrackingStatus by code
     * 
     * @param code the tracking status code
     * @return TrackingStatus or null if not found
     */
    public static TrackingStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        
        for (TrackingStatus status : values()) {
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
    public boolean canTransitionTo(TrackingStatus targetStatus) {
        if (targetStatus == null) {
            return false;
        }
        
        // Allow transition to same status (idempotent)
        if (this == targetStatus) {
            return true;
        }
        
        // No transitions from final states
        if (this.isFinal) {
            return false;
        }
        
        // Allow transition to problem states from most states
        if (targetStatus == LOST || targetStatus == DAMAGED || targetStatus == CANCELLED) {
            return !this.isFinal;
        }
        
        // Allow return process from delivered states
        if (targetStatus == RETURNING) {
            return this == DELIVERED || this == DELIVERY_CONFIRMED || this == PICKED_UP;
        }
        
        // Allow hold from most states
        if (targetStatus == ON_HOLD) {
            return !this.isFinal && this != DELIVERED && this != DELIVERY_CONFIRMED;
        }
        
        // Normal forward progression
        if (this.sequence > 0 && targetStatus.sequence > 0) {
            return targetStatus.sequence > this.sequence || 
                   (this == DELIVERY_ATTEMPTED && targetStatus == OUT_FOR_DELIVERY) ||
                   (this == DELIVERY_RESCHEDULED && targetStatus == OUT_FOR_DELIVERY);
        }
        
        return false;
    }
    
    /**
     * Check if this status indicates successful delivery
     * 
     * @return true if successfully delivered
     */
    public boolean isDelivered() {
        return this == DELIVERED || this == DELIVERY_CONFIRMED || this == PICKED_UP;
    }
    
    /**
     * Check if this status indicates a problem
     * 
     * @return true if there's a problem
     */
    public boolean isProblem() {
        return this.sequence < 0 || this == ON_HOLD || this == DELIVERY_ATTEMPTED || 
               this == SIGNATURE_REQUIRED;
    }
    
    /**
     * Check if this status requires customer action
     * 
     * @return true if customer action is required
     */
    public boolean requiresCustomerAction() {
        return this == READY_FOR_PICKUP || this == SIGNATURE_REQUIRED || 
               this == DELIVERY_ATTEMPTED || this == DELIVERY_RESCHEDULED;
    }
    
    /**
     * Check if tracking updates are expected for this status
     * 
     * @return true if more updates are expected
     */
    public boolean expectsMoreUpdates() {
        return !this.isFinal && this != ON_HOLD;
    }
    
    /**
     * Get the estimated delivery progress percentage
     * 
     * @return progress percentage (0-100)
     */
    public int getProgressPercentage() {
        return switch (this) {
            case ORDER_PLACED -> 5;
            case PROCESSING -> 10;
            case PICKING -> 15;
            case PACKING -> 20;
            case READY_FOR_SHIPMENT -> 25;
            case SHIPPED -> 30;
            case IN_TRANSIT -> 50;
            case IN_CUSTOMS -> 55;
            case CUSTOMS_CLEARED -> 60;
            case ARRIVED_AT_FACILITY -> 70;
            case OUT_FOR_DELIVERY -> 85;
            case READY_FOR_PICKUP -> 90;
            case DELIVERY_ATTEMPTED, DELIVERY_RESCHEDULED -> 80;
            case DELIVERED, DELIVERY_CONFIRMED, PICKED_UP -> 100;
            case SIGNATURE_REQUIRED -> 95;
            default -> 0;
        };
    }
    
    /**
     * Get the priority level for this tracking status
     * 
     * @return priority level (1 = highest, 5 = lowest)
     */
    public int getPriorityLevel() {
        return switch (this) {
            case LOST, DAMAGED -> 1;
            case DELIVERY_ATTEMPTED, ON_HOLD -> 2;
            case OUT_FOR_DELIVERY, READY_FOR_PICKUP -> 3;
            case IN_TRANSIT, SHIPPED -> 4;
            default -> 5;
        };
    }
}
