package org.de013.orderservice.dto.response;

import lombok.*;
import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.entity.enums.OrderType;
import org.de013.orderservice.entity.valueobject.Money;

import java.time.LocalDateTime;

/**
 * Order Summary Response DTO
 * 
 * Lightweight order information for list views and summaries.
 * Contains essential order data without detailed nested objects.
 * 
 * @author Development Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderSummaryResponse {
    
    /**
     * Order ID
     */
    private Long id;
    
    /**
     * Unique order number
     */
    private String orderNumber;
    
    /**
     * User ID who placed the order
     */
    private Long userId;
    
    /**
     * Customer name (from shipping address)
     */
    private String customerName;
    
    /**
     * Customer email (from shipping address)
     */
    private String customerEmail;
    
    /**
     * Current order status
     */
    private OrderStatus status;
    
    /**
     * Order type
     */
    private OrderType orderType;
    
    /**
     * Total order amount
     */
    private Money totalAmount;
    
    /**
     * Order source
     */
    private String orderSource;
    
    /**
     * Expected delivery date
     */
    private LocalDateTime expectedDeliveryDate;
    
    /**
     * Actual delivery date
     */
    private LocalDateTime actualDeliveryDate;
    
    /**
     * Order creation timestamp
     */
    private LocalDateTime createdAt;
    
    /**
     * Last update timestamp
     */
    private LocalDateTime updatedAt;
    
    /**
     * Priority level
     */
    private Integer priorityLevel;
    
    /**
     * Gift order flag
     */
    private Boolean isGift;
    
    /**
     * Special handling requirement
     */
    private Boolean requiresSpecialHandling;
    
    /**
     * Shipping city
     */
    private String shippingCity;
    
    /**
     * Shipping state
     */
    private String shippingState;
    
    /**
     * Shipping country
     */
    private String shippingCountry;
    
    /**
     * Order summary statistics
     */
    private OrderSummaryStats summaryStats;
    
    /**
     * Latest tracking information
     */
    private LatestTrackingInfo latestTracking;
    
    /**
     * Payment summary
     */
    private PaymentSummary paymentSummary;
    
    /**
     * Order Summary Statistics
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderSummaryStats {
        
        /**
         * Total number of items
         */
        private Integer totalItems;
        
        /**
         * Total quantity of all items
         */
        private Integer totalQuantity;
        
        /**
         * Order age in hours
         */
        private Long orderAgeHours;
        
        /**
         * Days since order placed
         */
        private Long daysSinceOrdered;
        
        /**
         * Whether order is overdue
         */
        private Boolean isOverdue;
        
        /**
         * Whether order is expedited
         */
        private Boolean isExpedited;
        
        /**
         * Whether order is paid
         */
        private Boolean isPaid;
        
        /**
         * Whether order is shipped
         */
        private Boolean isShipped;
        
        /**
         * Whether order is delivered
         */
        private Boolean isDelivered;
        
        /**
         * Whether order can be cancelled
         */
        private Boolean canBeCancelled;
        
        /**
         * Whether order is in final state
         */
        private Boolean isFinalState;
        
        /**
         * Estimated delivery days remaining
         */
        private Long deliveryDaysRemaining;
        
        /**
         * Order progress percentage (0-100)
         */
        private Integer progressPercentage;
    }
    
    /**
     * Latest Tracking Information
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LatestTrackingInfo {
        
        /**
         * Latest tracking status
         */
        private String trackingStatus;
        
        /**
         * Latest tracking location
         */
        private String location;
        
        /**
         * Latest tracking timestamp
         */
        private LocalDateTime timestamp;
        
        /**
         * Tracking number
         */
        private String trackingNumber;
        
        /**
         * Carrier name
         */
        private String carrier;
        
        /**
         * Estimated delivery date
         */
        private LocalDateTime estimatedDeliveryDate;
        
        /**
         * Whether tracking requires customer action
         */
        private Boolean requiresCustomerAction;
        
        /**
         * Whether this is a problem status
         */
        private Boolean isProblemStatus;
        
        /**
         * Progress percentage for this status
         */
        private Integer progressPercentage;
    }
    
    /**
     * Payment Summary
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaymentSummary {
        
        /**
         * Payment status
         */
        private String paymentStatus;
        
        /**
         * Payment method
         */
        private String paymentMethod;
        
        /**
         * Total paid amount
         */
        private Money totalPaidAmount;
        
        /**
         * Total refunded amount
         */
        private Money totalRefundedAmount;
        
        /**
         * Available refund amount
         */
        private Money availableRefundAmount;
        
        /**
         * Whether payment is successful
         */
        private Boolean isSuccessful;
        
        /**
         * Whether payment can be refunded
         */
        private Boolean canBeRefunded;
        
        /**
         * Whether payment is fully refunded
         */
        private Boolean isFullyRefunded;
        
        /**
         * Risk score
         */
        private Integer riskScore;
        
        /**
         * Last payment timestamp
         */
        private LocalDateTime lastPaymentAt;
    }
    
    /**
     * Get customer display name
     */
    public String getCustomerDisplayName() {
        return customerName != null ? customerName : "Customer #" + userId;
    }
    
    /**
     * Get shipping location display
     */
    public String getShippingLocationDisplay() {
        StringBuilder location = new StringBuilder();
        
        if (shippingCity != null) {
            location.append(shippingCity);
        }
        
        if (shippingState != null) {
            if (location.length() > 0) {
                location.append(", ");
            }
            location.append(shippingState);
        }
        
        if (shippingCountry != null) {
            if (location.length() > 0) {
                location.append(", ");
            }
            location.append(shippingCountry);
        }
        
        return location.toString();
    }
    
    /**
     * Get order status display with color coding
     */
    public String getStatusDisplay() {
        if (status == null) {
            return "UNKNOWN";
        }
        
        return status.getDescription();
    }
    
    /**
     * Get order type display
     */
    public String getOrderTypeDisplay() {
        if (orderType == null) {
            return "STANDARD";
        }
        
        return orderType.getDisplayName();
    }
    
    /**
     * Check if order needs attention
     */
    public boolean needsAttention() {
        if (summaryStats == null) {
            return false;
        }
        
        return Boolean.TRUE.equals(summaryStats.getIsOverdue()) ||
               (latestTracking != null && Boolean.TRUE.equals(latestTracking.getIsProblemStatus())) ||
               (paymentSummary != null && !Boolean.TRUE.equals(paymentSummary.getIsSuccessful()));
    }
    
    /**
     * Get attention reason
     */
    public String getAttentionReason() {
        if (!needsAttention()) {
            return null;
        }
        
        if (summaryStats != null && Boolean.TRUE.equals(summaryStats.getIsOverdue())) {
            return "Order is overdue";
        }
        
        if (latestTracking != null && Boolean.TRUE.equals(latestTracking.getIsProblemStatus())) {
            return "Shipping issue: " + latestTracking.getTrackingStatus();
        }
        
        if (paymentSummary != null && !Boolean.TRUE.equals(paymentSummary.getIsSuccessful())) {
            return "Payment issue: " + paymentSummary.getPaymentStatus();
        }
        
        return "Requires attention";
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
     * Get estimated delivery status
     */
    public String getDeliveryStatus() {
        if (actualDeliveryDate != null) {
            return "DELIVERED";
        }
        
        if (summaryStats != null && Boolean.TRUE.equals(summaryStats.getIsShipped())) {
            if (Boolean.TRUE.equals(summaryStats.getIsOverdue())) {
                return "OVERDUE";
            }
            return "IN_TRANSIT";
        }
        
        if (summaryStats != null && Boolean.TRUE.equals(summaryStats.getIsPaid())) {
            return "PROCESSING";
        }
        
        return "PENDING";
    }
    
    /**
     * Get days until expected delivery
     */
    public Long getDaysUntilDelivery() {
        if (expectedDeliveryDate == null || actualDeliveryDate != null) {
            return null;
        }
        
        LocalDateTime now = LocalDateTime.now();
        if (expectedDeliveryDate.isBefore(now)) {
            return 0L; // Overdue
        }
        
        return java.time.Duration.between(now, expectedDeliveryDate).toDays();
    }
    
    /**
     * Check if order is recent (within last 24 hours)
     */
    public boolean isRecent() {
        if (createdAt == null) {
            return false;
        }
        
        return java.time.Duration.between(createdAt, LocalDateTime.now()).toHours() <= 24;
    }
    
    /**
     * Get order age display
     */
    public String getOrderAgeDisplay() {
        if (summaryStats == null || summaryStats.getOrderAgeHours() == null) {
            return "Unknown";
        }
        
        long hours = summaryStats.getOrderAgeHours();
        
        if (hours < 24) {
            return hours + " hours ago";
        }
        
        long days = hours / 24;
        if (days == 1) {
            return "1 day ago";
        }
        
        return days + " days ago";
    }
}
