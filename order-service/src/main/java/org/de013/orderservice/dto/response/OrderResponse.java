package org.de013.orderservice.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
     * Check if billing address is same as shipping
     */
    @JsonIgnore
    public boolean isBillingSameAsShipping() {
        return billingAddress == null || billingAddress.equals(shippingAddress);
    }

    /**
     * Get effective billing address
     */
    @JsonIgnore
    public Address getEffectiveBillingAddress() {
        return billingAddress != null ? billingAddress : shippingAddress;
    }



    /**
     * Check if order is international
     */
    @JsonIgnore
    public boolean isInternational(String businessCountry) {
        return shippingAddress != null &&
               !shippingAddress.isDomestic(businessCountry);
    }
}
