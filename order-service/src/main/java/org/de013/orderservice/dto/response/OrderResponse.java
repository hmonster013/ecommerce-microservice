package org.de013.orderservice.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.entity.enums.OrderType;
import org.de013.orderservice.entity.valueobject.Address;
import org.de013.orderservice.entity.valueobject.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Order Response DTO
 */
public record OrderResponse(
        // Order ID
        Long id,

        // Unique order number
        String orderNumber,

        // User ID who placed the order
        String userId,

        // Current order status
        OrderStatus status,

        // Order type
        OrderType orderType,

        // Total order amount
        Money totalAmount,

        // Subtotal amount (before taxes and fees)
        Money subtotalAmount,

        // Tax amount
        Money taxAmount,

        // Shipping amount
        Money shippingAmount,

        // Discount amount
        Money discountAmount,

        // Shipping address
        Address shippingAddress,

        // Billing address
        Address billingAddress,

        // Customer notes
        String customerNotes,

        // Internal notes
        String internalNotes,

        // Order source
        String orderSource,

        // Expected delivery date
        LocalDateTime expectedDeliveryDate,

        // Actual delivery date
        LocalDateTime actualDeliveryDate,

        // Order confirmation date
        LocalDateTime confirmedAt,

        // Order cancellation date
        LocalDateTime cancelledAt,

        // Cancellation reason
        String cancellationReason,

        // Priority level
        Integer priorityLevel,

        // Special handling requirement
        Boolean requiresSpecialHandling,

        // Gift order flag
        Boolean isGift,

        // Gift message
        String giftMessage,

        // Order creation timestamp
        LocalDateTime createdAt,

        // Last update timestamp
        LocalDateTime updatedAt,

        // Order items
        List<OrderItemResponse> orderItems,

        // Order summary statistics
        OrderSummaryStats summaryStats
) {

    /**
     * Order Summary Statistics
     */
    public record OrderSummaryStats(
            // Total number of items
            Integer totalItems,

            // Total quantity of all items
            Integer totalQuantity,

            // Number of unique products
            Integer uniqueProducts,

            // Order age in hours
            Long orderAgeHours,

            // Days since order placed
            Long daysSinceOrdered,

            // Whether order is overdue
            Boolean isOverdue,

            // Whether order is expedited
            Boolean isExpedited,

            // Whether order is paid
            Boolean isPaid,

            // Whether order is shipped
            Boolean isShipped,

            // Whether order is delivered
            Boolean isDelivered,

            // Whether order can be cancelled
            Boolean canBeCancelled,

            // Whether order can be modified
            Boolean canBeModified,

            // Whether order is in final state
            Boolean isFinalState,

            // Estimated delivery days remaining
            Long deliveryDaysRemaining,

            // Order progress percentage (0-100)
            Integer progressPercentage
    ) {
    }

    /**
     * Order Item Response
     */
    public record OrderItemResponse(
            // Order item ID
            Long id,

            // Product ID
            String productId,

            // Product SKU
            String sku,

            // Product name
            String productName,

            // Product description
            String productDescription,

            // Product category
            String productCategory,

            // Product brand
            String productBrand,

            // Quantity ordered
            Integer quantity,

            // Unit price
            Money unitPrice,

            // Total price for this line item
            Money totalPrice,

            // Discount amount
            Money discountAmount,

            // Tax amount
            Money taxAmount,

            // Final price after discount
            Money finalPrice,

            // Price including tax
            Money priceIncludingTax,

            // Product weight
            BigDecimal weight,

            // Weight unit
            String weightUnit,

            // Product dimensions
            String dimensions,

            // Product image URL
            String productImageUrl,

            // Product variant information
            String variantInfo,

            // Special instructions
            String specialInstructions,

            // Gift item flag
            Boolean isGift,

            // Gift wrap type
            String giftWrapType,

            // Gift message
            String giftMessage,

            // Special handling requirement
            Boolean requiresSpecialHandling,

            // Fragile item flag
            Boolean isFragile,

            // Hazardous item flag
            Boolean isHazardous,

            // Expected delivery date
            LocalDateTime expectedDeliveryDate,

            // Actual delivery date
            LocalDateTime actualDeliveryDate,

            // Item status
            String status,

            // Discount percentage
            BigDecimal discountPercentage,

            // Total weight for this line item
            BigDecimal totalWeight,

            // Whether item is delivered
            Boolean isDelivered,

            // Whether item is overdue
            Boolean isOverdue
    ) {
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
