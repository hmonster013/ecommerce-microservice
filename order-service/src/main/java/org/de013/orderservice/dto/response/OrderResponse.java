package org.de013.orderservice.dto.response;

import lombok.*;
import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.entity.enums.OrderType;
import org.de013.orderservice.entity.valueobject.Address;
import org.de013.orderservice.entity.valueobject.Money;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Order Response DTO
 * 
 * Complete order information response including all related data.
 * Used for detailed order views and order management operations.
 * 
 * @author Development Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {
    
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
     * Subtotal amount (before taxes and fees)
     */
    private Money subtotalAmount;
    
    /**
     * Tax amount
     */
    private Money taxAmount;
    
    /**
     * Shipping amount
     */
    private Money shippingAmount;
    
    /**
     * Discount amount
     */
    private Money discountAmount;
    
    /**
     * Shipping address
     */
    private Address shippingAddress;
    
    /**
     * Billing address
     */
    private Address billingAddress;
    
    /**
     * Customer notes
     */
    private String customerNotes;
    
    /**
     * Internal notes
     */
    private String internalNotes;
    
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
     * Order confirmation date
     */
    private LocalDateTime confirmedAt;
    
    /**
     * Order cancellation date
     */
    private LocalDateTime cancelledAt;
    
    /**
     * Cancellation reason
     */
    private String cancellationReason;
    
    /**
     * Priority level
     */
    private Integer priorityLevel;
    
    /**
     * Special handling requirement
     */
    private Boolean requiresSpecialHandling;
    
    /**
     * Gift order flag
     */
    private Boolean isGift;
    
    /**
     * Gift message
     */
    private String giftMessage;
    
    /**
     * Order creation timestamp
     */
    private LocalDateTime createdAt;
    
    /**
     * Last update timestamp
     */
    private LocalDateTime updatedAt;
    
    /**
     * Order items
     */
    private List<OrderItemResponse> orderItems;
    
    /**
     * Order tracking history
     */
    private List<OrderTrackingResponse> trackingHistory;
    
    /**
     * Order payments
     */
    private List<OrderPaymentResponse> payments;
    
    /**
     * Order shipping information
     */
    private OrderShippingResponse shipping;
    
    /**
     * Order summary statistics
     */
    private OrderSummaryStats summaryStats;
    
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
         * Number of unique products
         */
        private Integer uniqueProducts;
        
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
         * Whether order can be modified
         */
        private Boolean canBeModified;
        
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
     * Order Item Response
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemResponse {
        
        /**
         * Order item ID
         */
        private Long id;
        
        /**
         * Product ID
         */
        private Long productId;
        
        /**
         * Product SKU
         */
        private String sku;
        
        /**
         * Product name
         */
        private String productName;
        
        /**
         * Product description
         */
        private String productDescription;
        
        /**
         * Product category
         */
        private String productCategory;
        
        /**
         * Product brand
         */
        private String productBrand;
        
        /**
         * Quantity ordered
         */
        private Integer quantity;
        
        /**
         * Unit price
         */
        private Money unitPrice;
        
        /**
         * Total price for this line item
         */
        private Money totalPrice;
        
        /**
         * Discount amount
         */
        private Money discountAmount;
        
        /**
         * Tax amount
         */
        private Money taxAmount;
        
        /**
         * Final price after discount
         */
        private Money finalPrice;
        
        /**
         * Price including tax
         */
        private Money priceIncludingTax;
        
        /**
         * Product weight
         */
        private java.math.BigDecimal weight;
        
        /**
         * Weight unit
         */
        private String weightUnit;
        
        /**
         * Product dimensions
         */
        private String dimensions;
        
        /**
         * Product image URL
         */
        private String productImageUrl;
        
        /**
         * Product variant information
         */
        private String variantInfo;
        
        /**
         * Special instructions
         */
        private String specialInstructions;
        
        /**
         * Gift item flag
         */
        private Boolean isGift;
        
        /**
         * Gift wrap type
         */
        private String giftWrapType;
        
        /**
         * Gift message
         */
        private String giftMessage;
        
        /**
         * Special handling requirement
         */
        private Boolean requiresSpecialHandling;
        
        /**
         * Fragile item flag
         */
        private Boolean isFragile;
        
        /**
         * Hazardous item flag
         */
        private Boolean isHazardous;
        
        /**
         * Expected delivery date
         */
        private LocalDateTime expectedDeliveryDate;
        
        /**
         * Actual delivery date
         */
        private LocalDateTime actualDeliveryDate;
        
        /**
         * Item status
         */
        private String status;
        
        /**
         * Discount percentage
         */
        private java.math.BigDecimal discountPercentage;
        
        /**
         * Total weight for this line item
         */
        private java.math.BigDecimal totalWeight;
        
        /**
         * Whether item is delivered
         */
        private Boolean isDelivered;
        
        /**
         * Whether item is overdue
         */
        private Boolean isOverdue;
    }
    
    /**
     * Order Payment Response
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderPaymentResponse {
        
        /**
         * Payment ID
         */
        private Long id;
        
        /**
         * External payment ID
         */
        private String paymentId;
        
        /**
         * Transaction ID
         */
        private String transactionId;
        
        /**
         * Payment method
         */
        private String paymentMethod;
        
        /**
         * Payment method details (masked)
         */
        private String paymentMethodDetails;
        
        /**
         * Payment status
         */
        private String status;
        
        /**
         * Payment amount
         */
        private Money amount;
        
        /**
         * Authorized amount
         */
        private Money authorizedAmount;
        
        /**
         * Captured amount
         */
        private Money capturedAmount;
        
        /**
         * Refunded amount
         */
        private Money refundedAmount;
        
        /**
         * Available refund amount
         */
        private Money availableRefundAmount;
        
        /**
         * Payment gateway
         */
        private String paymentGateway;
        
        /**
         * Authorization code
         */
        private String authorizationCode;
        
        /**
         * Risk score
         */
        private Integer riskScore;
        
        /**
         * Risk assessment
         */
        private String riskAssessment;
        
        /**
         * Payment timestamps
         */
        private LocalDateTime initiatedAt;
        private LocalDateTime processedAt;
        private LocalDateTime authorizedAt;
        private LocalDateTime capturedAt;
        private LocalDateTime failedAt;
        private LocalDateTime refundedAt;
        
        /**
         * Failure reason
         */
        private String failureReason;
        
        /**
         * Refund reason
         */
        private String refundReason;
        
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
         * Refund percentage
         */
        private java.math.BigDecimal refundPercentage;
        
        /**
         * Processing time in minutes
         */
        private Long processingTimeMinutes;
    }
    
    /**
     * Check if billing address is same as shipping
     */
    public boolean isBillingSameAsShipping() {
        return billingAddress == null || billingAddress.equals(shippingAddress);
    }
    
    /**
     * Get effective billing address
     */
    public Address getEffectiveBillingAddress() {
        return billingAddress != null ? billingAddress : shippingAddress;
    }
    
    /**
     * Get latest tracking status
     */
    public OrderTrackingResponse getLatestTracking() {
        if (trackingHistory == null || trackingHistory.isEmpty()) {
            return null;
        }
        return trackingHistory.get(0);
    }
    
    /**
     * Get latest payment
     */
    public OrderPaymentResponse getLatestPayment() {
        if (payments == null || payments.isEmpty()) {
            return null;
        }
        return payments.get(0);
    }
    
    /**
     * Check if order has any successful payments
     */
    public boolean hasSuccessfulPayment() {
        return payments != null && payments.stream()
                .anyMatch(payment -> Boolean.TRUE.equals(payment.getIsSuccessful()));
    }
    
    /**
     * Get total refunded amount across all payments
     */
    public Money getTotalRefundedAmount() {
        if (payments == null || payments.isEmpty()) {
            return Money.zero(totalAmount.getCurrency());
        }
        
        return payments.stream()
                .filter(payment -> payment.getRefundedAmount() != null)
                .map(OrderPaymentResponse::getRefundedAmount)
                .reduce(Money.zero(totalAmount.getCurrency()), Money::add);
    }
    
    /**
     * Check if order is international
     */
    public boolean isInternational(String businessCountry) {
        return shippingAddress != null && 
               !shippingAddress.isDomestic(businessCountry);
    }
}
