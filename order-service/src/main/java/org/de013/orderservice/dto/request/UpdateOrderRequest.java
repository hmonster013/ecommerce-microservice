package org.de013.orderservice.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.entity.valueobject.Address;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Update Order Request DTO
 */
public record UpdateOrderRequest(
        // ID of the order to update
        @NotNull(message = "{order.id.required}")
        @Positive(message = "{order.id.positive}")
        Long orderId,

        // New order status (if updating status)
        OrderStatus status,

        // Updated shipping address
        @Valid
        Address shippingAddress,

        // Updated billing address
        @Valid
        Address billingAddress,

        // Updated customer notes
        @Size(max = 2000, message = "{customer.notes.size}")
        String customerNotes,

        // Updated internal notes
        @Size(max = 2000, message = "{internal.notes.size}")
        String internalNotes,

        // Updated expected delivery date
        LocalDateTime expectedDeliveryDate,

        // Updated preferred delivery date
        LocalDateTime preferredDeliveryDate,

        // Updated priority level (1-5)
        @Min(value = 1, message = "{priority.level.range}")
        @Max(value = 5, message = "{priority.level.range}")
        Integer priorityLevel,

        // Updated special handling requirement
        Boolean requiresSpecialHandling,

        // Updated gift status
        Boolean isGift,

        // Updated gift message
        @Size(max = 1000, message = "{gift.message.size}")
        String giftMessage,

        // Updated shipping method
        @Size(max = 50, message = "{field.size.max}")
        String shippingMethod,

        // Updated delivery instructions
        @Size(max = 1000, message = "{delivery.instructions.size}")
        String deliveryInstructions,

        // Updated signature requirement
        Boolean signatureRequired,

        // Updated adult signature requirement
        Boolean adultSignatureRequired,

        // Updated insurance requirement
        Boolean purchaseInsurance,

        // Updated insurance value
        @DecimalMin(value = "0.0", message = "{insurance.value.non-negative}")
        BigDecimal insuranceValue,

        // Order items to update
        @Valid
        List<UpdateOrderItemDto> orderItems,

        // Reason for the update (for audit purposes)
        @Size(max = 500, message = "{update.reason.size}")
        String updateReason,

        // User ID who is making the update
        @Positive(message = "{updated.by.user.id.positive}")
        Long updatedByUserId,

        // Whether to send notification about the update
        Boolean sendNotification,

        // Additional metadata for the update
        String metadata
) {

    public UpdateOrderRequest {
        if (sendNotification == null) {
            sendNotification = true;
        }
    }

    /**
     * Update Order Item DTO
     */
    public record UpdateOrderItemDto(
            // ID of the order item to update
            @NotNull(message = "{order.item.id.required}")
            @Positive(message = "{order.item.id.positive}")
            Long orderItemId,

            // Updated quantity
            @Positive(message = "{order.item.quantity.positive}")
            Integer quantity,

            // Updated unit price
            @DecimalMin(value = "0.0", message = "{order.item.price.non-negative}")
            BigDecimal unitPrice,

            // Updated discount amount
            @DecimalMin(value = "0.0", message = "{order.item.discount.non-negative}")
            BigDecimal discountAmount,

            // Updated special instructions
            @Size(max = 1000, message = "{order.item.instructions.size}")
            String specialInstructions,

            // Updated gift status for this item
            Boolean isGift,

            // Updated gift wrap type
            @Size(max = 100, message = "{order.item.gift.wrap.size}")
            String giftWrapType,

            // Updated gift message for this item
            @Size(max = 500, message = "{order.item.gift.message.size}")
            String giftMessage,

            // Updated expected delivery date for this item
            LocalDateTime expectedDeliveryDate,

            // Action to perform on this item (UPDATE, REMOVE)
            @NotBlank(message = "{order.item.action.required}")
            @Pattern(regexp = "^(UPDATE|REMOVE)$", message = "{order.item.action.format}")
            String action
    ) {
    }

    /**
     * Check if this update includes status change
     */
    @JsonIgnore
    public boolean hasStatusUpdate() {
        return status != null;
    }

    /**
     * Check if this update includes address changes
     */
    @JsonIgnore
    public boolean hasAddressUpdate() {
        return shippingAddress != null || billingAddress != null;
    }

    /**
     * Validate that order can be updated based on current status (simplified for base version)
     */
    @JsonIgnore
    public boolean isValidForStatus(OrderStatus currentStatus) {
        if (currentStatus == null) {
            return false;
        }

        // Only allow updates for certain statuses
        return currentStatus == OrderStatus.PENDING ||
                currentStatus == OrderStatus.CONFIRMED ||
                currentStatus == OrderStatus.PAID;
    }
}
