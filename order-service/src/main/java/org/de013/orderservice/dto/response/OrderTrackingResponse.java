package org.de013.orderservice.dto.response;

import lombok.*;
import org.de013.orderservice.entity.enums.TrackingStatus;

import java.time.LocalDateTime;

/**
 * Order Tracking Response DTO
 * 
 * Response object containing order tracking information and history.
 * Used for tracking updates and shipment monitoring.
 * 
 * @author Development Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderTrackingResponse {
    
    /**
     * Tracking record ID
     */
    private Long id;
    
    /**
     * Order ID
     */
    private Long orderId;
    
    /**
     * Tracking status
     */
    private TrackingStatus trackingStatus;
    
    /**
     * Status display name
     */
    private String statusDisplayName;
    
    /**
     * Status description
     */
    private String statusDescription;
    
    /**
     * Location where the status update occurred
     */
    private String location;
    
    /**
     * Detailed location information
     */
    private String locationDetails;
    
    /**
     * Complete formatted location
     */
    private String completeLocation;
    
    /**
     * City
     */
    private String city;
    
    /**
     * State/Province
     */
    private String state;
    
    /**
     * Country
     */
    private String country;
    
    /**
     * Postal code
     */
    private String postalCode;
    
    /**
     * Timestamp when the status update occurred
     */
    private LocalDateTime timestamp;
    
    /**
     * Additional notes
     */
    private String notes;
    
    /**
     * Tracking number
     */
    private String trackingNumber;
    
    /**
     * Shipping carrier
     */
    private String carrier;
    
    /**
     * Carrier service type
     */
    private String carrierService;
    
    /**
     * Estimated delivery date
     */
    private LocalDateTime estimatedDeliveryDate;
    
    /**
     * Actual delivery date
     */
    private LocalDateTime actualDeliveryDate;
    
    /**
     * Delivery attempt number
     */
    private Integer deliveryAttempt;
    
    /**
     * Delivery failure reason
     */
    private String deliveryFailureReason;
    
    /**
     * Person who received the package
     */
    private String receivedBy;
    
    /**
     * Signature requirement
     */
    private Boolean signatureRequired;
    
    /**
     * Signature obtained
     */
    private Boolean signatureObtained;
    
    /**
     * Proof of delivery URL
     */
    private String proofOfDeliveryUrl;
    
    /**
     * Whether this is an automated update
     */
    private Boolean isAutomated;
    
    /**
     * Update source
     */
    private String updateSource;
    
    /**
     * User who created this update
     */
    private Long updatedByUserId;
    
    /**
     * External tracking ID
     */
    private String externalTrackingId;
    
    /**
     * Whether visible to customer
     */
    private Boolean isCustomerVisible;
    
    /**
     * Priority level
     */
    private Integer priorityLevel;
    
    /**
     * Progress percentage (0-100)
     */
    private Integer progressPercentage;
    
    /**
     * Whether this indicates delivery
     */
    private Boolean isDeliveryUpdate;
    
    /**
     * Whether this indicates a problem
     */
    private Boolean isProblemUpdate;
    
    /**
     * Whether this requires customer action
     */
    private Boolean requiresCustomerAction;
    
    /**
     * Whether this is a delivery attempt
     */
    private Boolean isDeliveryAttempt;
    
    /**
     * Whether delivery was successful
     */
    private Boolean isDeliverySuccessful;
    
    /**
     * Whether delivery failed
     */
    private Boolean isDeliveryFailed;
    
    /**
     * Hours since this update
     */
    private Long hoursSinceUpdate;
    
    /**
     * Whether this update is recent (within 24 hours)
     */
    private Boolean isRecent;
    
    /**
     * Tracking URL for carrier website
     */
    private String trackingUrl;
    
    /**
     * Additional metadata
     */
    private String metadata;
    
    /**
     * Record creation timestamp
     */
    private LocalDateTime createdAt;
    
    /**
     * Get status display with icon
     */
    public String getStatusDisplayWithIcon() {
        if (trackingStatus == null) {
            return "Unknown";
        }
        
        String icon = switch (trackingStatus) {
            case ORDER_PLACED -> "📋";
            case PROCESSING -> "⚙️";
            case PICKING -> "📦";
            case PACKING -> "📦";
            case READY_FOR_SHIPMENT -> "🚚";
            case SHIPPED -> "🚛";
            case IN_TRANSIT -> "🚚";
            case ARRIVED_AT_FACILITY -> "🏢";
            case OUT_FOR_DELIVERY -> "🚚";
            case DELIVERED -> "✅";
            case DELIVERY_CONFIRMED -> "✅";
            case PICKED_UP -> "✅";
            case DELIVERY_ATTEMPTED -> "⚠️";
            case DELIVERY_RESCHEDULED -> "📅";
            case SIGNATURE_REQUIRED -> "✍️";
            case READY_FOR_PICKUP -> "📍";
            case IN_CUSTOMS -> "🛃";
            case CUSTOMS_CLEARED -> "✅";
            case RETURNING -> "↩️";
            case RETURNED -> "↩️";
            case LOST -> "❌";
            case DAMAGED -> "💥";
            case CANCELLED -> "❌";
            case ON_HOLD -> "⏸️";
        };
        
        return icon + " " + trackingStatus.getDisplayName();
    }
    
    /**
     * Get priority display
     */
    public String getPriorityDisplay() {
        if (priorityLevel == null) {
            return "NORMAL";
        }
        
        return switch (priorityLevel) {
            case 1 -> "URGENT";
            case 2 -> "HIGH";
            case 3 -> "NORMAL";
            case 4 -> "LOW";
            case 5 -> "LOWEST";
            default -> "NORMAL";
        };
    }
    
    /**
     * Get time since update display
     */
    public String getTimeSinceUpdateDisplay() {
        if (hoursSinceUpdate == null) {
            return "Unknown";
        }
        
        if (hoursSinceUpdate < 1) {
            return "Just now";
        }
        
        if (hoursSinceUpdate < 24) {
            return hoursSinceUpdate + " hours ago";
        }
        
        long days = hoursSinceUpdate / 24;
        if (days == 1) {
            return "1 day ago";
        }
        
        return days + " days ago";
    }
    
    /**
     * Get delivery status display
     */
    public String getDeliveryStatusDisplay() {
        if (Boolean.TRUE.equals(isDeliverySuccessful)) {
            return "Successfully delivered";
        }
        
        if (Boolean.TRUE.equals(isDeliveryFailed)) {
            return "Delivery failed: " + (deliveryFailureReason != null ? deliveryFailureReason : "Unknown reason");
        }
        
        if (Boolean.TRUE.equals(isDeliveryAttempt)) {
            return "Delivery attempted (Attempt #" + deliveryAttempt + ")";
        }
        
        if (Boolean.TRUE.equals(requiresCustomerAction)) {
            return "Customer action required";
        }
        
        return "In progress";
    }
    
    /**
     * Get location display for customer
     */
    public String getCustomerLocationDisplay() {
        if (completeLocation != null && !completeLocation.trim().isEmpty()) {
            return completeLocation;
        }
        
        StringBuilder locationBuilder = new StringBuilder();
        
        if (city != null && !city.trim().isEmpty()) {
            locationBuilder.append(city);
        }
        
        if (state != null && !state.trim().isEmpty()) {
            if (locationBuilder.length() > 0) {
                locationBuilder.append(", ");
            }
            locationBuilder.append(state);
        }
        
        if (country != null && !country.trim().isEmpty()) {
            if (locationBuilder.length() > 0) {
                locationBuilder.append(", ");
            }
            locationBuilder.append(country);
        }
        
        return locationBuilder.toString();
    }
    
    /**
     * Get estimated delivery display
     */
    public String getEstimatedDeliveryDisplay() {
        if (actualDeliveryDate != null) {
            return "Delivered on " + actualDeliveryDate.toLocalDate();
        }
        
        if (estimatedDeliveryDate != null) {
            LocalDateTime now = LocalDateTime.now();
            if (estimatedDeliveryDate.isBefore(now)) {
                return "Was expected " + estimatedDeliveryDate.toLocalDate() + " (Overdue)";
            }
            
            long daysUntil = java.time.Duration.between(now, estimatedDeliveryDate).toDays();
            if (daysUntil == 0) {
                return "Expected today";
            } else if (daysUntil == 1) {
                return "Expected tomorrow";
            } else {
                return "Expected in " + daysUntil + " days";
            }
        }
        
        return "Delivery date not available";
    }
    
    /**
     * Check if this tracking update is actionable by customer
     */
    public boolean isActionableByCustomer() {
        return Boolean.TRUE.equals(requiresCustomerAction) || 
               trackingStatus == TrackingStatus.READY_FOR_PICKUP ||
               trackingStatus == TrackingStatus.DELIVERY_RESCHEDULED ||
               trackingStatus == TrackingStatus.SIGNATURE_REQUIRED;
    }
    
    /**
     * Get action required message for customer
     */
    public String getCustomerActionMessage() {
        if (!isActionableByCustomer()) {
            return null;
        }
        
        return switch (trackingStatus) {
            case READY_FOR_PICKUP -> "Your package is ready for pickup at " + getCustomerLocationDisplay();
            case DELIVERY_RESCHEDULED -> "Delivery has been rescheduled. Please check for updates.";
            case SIGNATURE_REQUIRED -> "Signature required for delivery. Please be available.";
            case DELIVERY_ATTEMPTED -> "Delivery was attempted but failed. " + 
                                     (deliveryFailureReason != null ? deliveryFailureReason : "Please contact carrier.");
            default -> "Please check tracking details for required action.";
        };
    }
    
    /**
     * Get CSS class for status styling
     */
    public String getStatusCssClass() {
        if (trackingStatus == null) {
            return "status-unknown";
        }
        
        if (Boolean.TRUE.equals(isDeliverySuccessful)) {
            return "status-success";
        }
        
        if (Boolean.TRUE.equals(isProblemUpdate)) {
            return "status-error";
        }
        
        if (Boolean.TRUE.equals(requiresCustomerAction)) {
            return "status-warning";
        }
        
        if (trackingStatus.isInTransit()) {
            return "status-info";
        }
        
        return "status-default";
    }
}
