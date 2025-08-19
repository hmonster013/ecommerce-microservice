package org.de013.orderservice.dto.response;

import lombok.*;
import org.de013.orderservice.entity.valueobject.Address;
import org.de013.orderservice.entity.valueobject.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Order Shipping Response DTO
 * 
 * Response object containing comprehensive shipping information for an order.
 * Used for shipping management and customer tracking displays.
 * 
 * @author Development Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderShippingResponse {
    
    /**
     * Shipping record ID
     */
    private Long id;
    
    /**
     * Order ID
     */
    private Long orderId;
    
    /**
     * Shipping address
     */
    private Address shippingAddress;
    
    /**
     * Shipping method
     */
    private String shippingMethod;
    
    /**
     * Shipping method display name
     */
    private String shippingMethodDisplay;
    
    /**
     * Shipping carrier
     */
    private String carrier;
    
    /**
     * Carrier service type
     */
    private String carrierService;
    
    /**
     * Tracking number
     */
    private String trackingNumber;
    
    /**
     * Tracking URL
     */
    private String trackingUrl;
    
    /**
     * Shipping cost
     */
    private Money shippingCost;
    
    /**
     * Insurance cost
     */
    private Money insuranceCost;
    
    /**
     * Total shipping cost (shipping + insurance)
     */
    private Money totalShippingCost;
    
    /**
     * Total weight of shipment
     */
    private BigDecimal totalWeight;
    
    /**
     * Weight unit
     */
    private String weightUnit;
    
    /**
     * Package dimensions
     */
    private String dimensions;
    
    /**
     * Number of packages
     */
    private Integer packageCount;
    
    /**
     * Estimated delivery date
     */
    private LocalDateTime estimatedDeliveryDate;
    
    /**
     * Actual delivery date
     */
    private LocalDateTime actualDeliveryDate;
    
    /**
     * Shipped timestamp
     */
    private LocalDateTime shippedAt;
    
    /**
     * Label created timestamp
     */
    private LocalDateTime labelCreatedAt;
    
    /**
     * Shipping label URL
     */
    private String shippingLabelUrl;
    
    /**
     * Commercial invoice URL
     */
    private String commercialInvoiceUrl;
    
    /**
     * Customs declaration number
     */
    private String customsDeclarationNumber;
    
    /**
     * Signature requirement
     */
    private Boolean signatureRequired;
    
    /**
     * Adult signature requirement
     */
    private Boolean adultSignatureRequired;
    
    /**
     * Insurance status
     */
    private Boolean isInsured;
    
    /**
     * Insurance value
     */
    private Money insuranceValue;
    
    /**
     * International shipment flag
     */
    private Boolean isInternational;
    
    /**
     * Delivery confirmation requirement
     */
    private Boolean deliveryConfirmationRequired;
    
    /**
     * Special handling instructions
     */
    private String specialInstructions;
    
    /**
     * Delivery window start
     */
    private LocalDateTime deliveryWindowStart;
    
    /**
     * Delivery window end
     */
    private LocalDateTime deliveryWindowEnd;
    
    /**
     * Preferred delivery date
     */
    private LocalDateTime preferredDeliveryDate;
    
    /**
     * Current shipping status
     */
    private String shippingStatus;
    
    /**
     * Shipping status display
     */
    private String shippingStatusDisplay;
    
    /**
     * Return tracking number
     */
    private String returnTrackingNumber;
    
    /**
     * Return label URL
     */
    private String returnLabelUrl;
    
    /**
     * Shipping analytics
     */
    private ShippingAnalytics analytics;
    
    /**
     * Record timestamps
     */
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    /**
     * Shipping Analytics
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShippingAnalytics {
        
        /**
         * Whether shipment has been shipped
         */
        private Boolean isShipped;
        
        /**
         * Whether shipment has been delivered
         */
        private Boolean isDelivered;
        
        /**
         * Whether shipment is in transit
         */
        private Boolean isInTransit;
        
        /**
         * Whether shipment is overdue
         */
        private Boolean isOverdue;
        
        /**
         * Delivery time in hours
         */
        private Long deliveryTimeHours;
        
        /**
         * Whether delivery was fast
         */
        private Boolean isFastDelivery;
        
        /**
         * Whether this is express shipment
         */
        private Boolean isExpress;
        
        /**
         * Whether label has been created
         */
        private Boolean isLabelCreated;
        
        /**
         * Whether requires special handling
         */
        private Boolean requiresSpecialHandling;
        
        /**
         * Whether delivery is within window
         */
        private Boolean isWithinDeliveryWindow;
        
        /**
         * Estimated delivery days
         */
        private Long estimatedDeliveryDays;
        
        /**
         * Whether customs documentation is complete
         */
        private Boolean isCustomsDocumentationComplete;
        
        /**
         * Days until expected delivery
         */
        private Long daysUntilDelivery;
        
        /**
         * Shipping progress percentage (0-100)
         */
        private Integer progressPercentage;
    }
    
    /**
     * Get shipping method display with carrier
     */
    public String getShippingMethodWithCarrier() {
        StringBuilder display = new StringBuilder();
        
        if (shippingMethod != null) {
            display.append(shippingMethod);
        }
        
        if (carrier != null) {
            if (display.length() > 0) {
                display.append(" via ");
            }
            display.append(carrier);
        }
        
        if (carrierService != null) {
            display.append(" (").append(carrierService).append(")");
        }
        
        return display.toString();
    }
    
    /**
     * Get delivery status display
     */
    public String getDeliveryStatusDisplay() {
        if (actualDeliveryDate != null) {
            return "Delivered on " + actualDeliveryDate.toLocalDate();
        }
        
        if (analytics != null && Boolean.TRUE.equals(analytics.getIsShipped())) {
            if (Boolean.TRUE.equals(analytics.getIsOverdue())) {
                return "In transit (Overdue)";
            }
            return "In transit";
        }
        
        if (analytics != null && Boolean.TRUE.equals(analytics.getIsLabelCreated())) {
            return "Label created";
        }
        
        return "Preparing for shipment";
    }
    
    /**
     * Get estimated delivery display
     */
    public String getEstimatedDeliveryDisplay() {
        if (actualDeliveryDate != null) {
            return "Delivered";
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
                return "Expected in " + daysUntil + " days (" + estimatedDeliveryDate.toLocalDate() + ")";
            }
        }
        
        return "Delivery date not available";
    }
    
    /**
     * Get shipping address display
     */
    public String getShippingAddressDisplay() {
        if (shippingAddress == null) {
            return "No address";
        }
        
        return shippingAddress.getFormattedSingleLine();
    }
    
    /**
     * Get delivery window display
     */
    public String getDeliveryWindowDisplay() {
        if (deliveryWindowStart == null || deliveryWindowEnd == null) {
            return "No specific window";
        }
        
        if (deliveryWindowStart.toLocalDate().equals(deliveryWindowEnd.toLocalDate())) {
            return deliveryWindowStart.toLocalDate() + " between " +
                   deliveryWindowStart.toLocalTime() + " - " + deliveryWindowEnd.toLocalTime();
        }
        
        return deliveryWindowStart.toLocalDate() + " " + deliveryWindowStart.toLocalTime() +
               " to " + deliveryWindowEnd.toLocalDate() + " " + deliveryWindowEnd.toLocalTime();
    }
    
    /**
     * Get special requirements display
     */
    public String getSpecialRequirementsDisplay() {
        java.util.List<String> requirements = new java.util.ArrayList<>();
        
        if (Boolean.TRUE.equals(signatureRequired)) {
            requirements.add("Signature required");
        }
        
        if (Boolean.TRUE.equals(adultSignatureRequired)) {
            requirements.add("Adult signature required");
        }
        
        if (Boolean.TRUE.equals(isInsured)) {
            requirements.add("Insured for " + (insuranceValue != null ? insuranceValue.format() : "full value"));
        }
        
        if (Boolean.TRUE.equals(deliveryConfirmationRequired)) {
            requirements.add("Delivery confirmation required");
        }
        
        if (specialInstructions != null && !specialInstructions.trim().isEmpty()) {
            requirements.add("Special instructions: " + specialInstructions);
        }
        
        return requirements.isEmpty() ? "No special requirements" : String.join(", ", requirements);
    }
    
    /**
     * Get package information display
     */
    public String getPackageInfoDisplay() {
        StringBuilder info = new StringBuilder();
        
        if (packageCount != null && packageCount > 1) {
            info.append(packageCount).append(" packages");
        } else {
            info.append("1 package");
        }
        
        if (totalWeight != null && totalWeight.compareTo(BigDecimal.ZERO) > 0) {
            info.append(", ").append(totalWeight).append(" ").append(weightUnit != null ? weightUnit : "kg");
        }
        
        if (dimensions != null && !dimensions.trim().isEmpty()) {
            info.append(", ").append(dimensions);
        }
        
        return info.toString();
    }
    
    /**
     * Check if tracking is available
     */
    public boolean isTrackingAvailable() {
        return trackingNumber != null && !trackingNumber.trim().isEmpty();
    }
    
    /**
     * Check if return is available
     */
    public boolean isReturnAvailable() {
        return returnTrackingNumber != null && !returnTrackingNumber.trim().isEmpty() &&
               returnLabelUrl != null && !returnLabelUrl.trim().isEmpty();
    }
    
    /**
     * Get shipping timeline display
     */
    public String getShippingTimelineDisplay() {
        StringBuilder timeline = new StringBuilder();
        
        if (labelCreatedAt != null) {
            timeline.append("Label created: ").append(labelCreatedAt.toLocalDate()).append("\n");
        }
        
        if (shippedAt != null) {
            timeline.append("Shipped: ").append(shippedAt.toLocalDate()).append("\n");
        }
        
        if (actualDeliveryDate != null) {
            timeline.append("Delivered: ").append(actualDeliveryDate.toLocalDate());
        } else if (estimatedDeliveryDate != null) {
            timeline.append("Expected delivery: ").append(estimatedDeliveryDate.toLocalDate());
        }
        
        return timeline.toString().trim();
    }
    
    /**
     * Get CSS class for status styling
     */
    public String getStatusCssClass() {
        if (actualDeliveryDate != null) {
            return "shipping-delivered";
        }
        
        if (analytics != null && Boolean.TRUE.equals(analytics.getIsOverdue())) {
            return "shipping-overdue";
        }
        
        if (analytics != null && Boolean.TRUE.equals(analytics.getIsInTransit())) {
            return "shipping-in-transit";
        }
        
        if (analytics != null && Boolean.TRUE.equals(analytics.getIsShipped())) {
            return "shipping-shipped";
        }
        
        return "shipping-preparing";
    }
}
