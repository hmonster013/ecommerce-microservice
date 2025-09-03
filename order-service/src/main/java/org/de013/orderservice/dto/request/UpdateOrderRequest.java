package org.de013.orderservice.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.entity.valueobject.Address;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Update Order Request DTO
 * 
 * Request object for updating an existing order.
 * Contains fields that can be modified after order creation.
 * 
 * @author Development Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateOrderRequest {
    
    /**
     * ID of the order to update
     */
    @NotNull(message = "{order.id.required}")
    @Positive(message = "{order.id.positive}")
    private Long orderId;
    
    /**
     * New order status (if updating status)
     */
    private OrderStatus status;
    
    /**
     * Updated shipping address
     */
    @Valid
    private Address shippingAddress;
    
    /**
     * Updated billing address
     */
    @Valid
    private Address billingAddress;
    
    /**
     * Updated customer notes
     */
    @Size(max = 2000, message = "{customer.notes.size}")
    private String customerNotes;
    
    /**
     * Updated internal notes
     */
    @Size(max = 2000, message = "{internal.notes.size}")
    private String internalNotes;
    
    /**
     * Updated expected delivery date
     */
    private LocalDateTime expectedDeliveryDate;
    
    /**
     * Updated preferred delivery date
     */
    private LocalDateTime preferredDeliveryDate;
    
    /**
     * Updated priority level (1-5)
     */
    @Min(value = 1, message = "{priority.level.range}")
    @Max(value = 5, message = "{priority.level.range}")
    private Integer priorityLevel;
    
    /**
     * Updated special handling requirement
     */
    private Boolean requiresSpecialHandling;
    
    /**
     * Updated gift status
     */
    private Boolean isGift;
    
    /**
     * Updated gift message
     */
    @Size(max = 1000, message = "{gift.message.size}")
    private String giftMessage;
    
    /**
     * Updated shipping method
     */
    @Size(max = 50, message = "{field.size.max}")
    private String shippingMethod;
    
    /**
     * Updated delivery instructions
     */
    @Size(max = 1000, message = "{delivery.instructions.size}")
    private String deliveryInstructions;
    
    /**
     * Updated signature requirement
     */
    private Boolean signatureRequired;
    
    /**
     * Updated adult signature requirement
     */
    private Boolean adultSignatureRequired;
    
    /**
     * Updated insurance requirement
     */
    private Boolean purchaseInsurance;
    
    /**
     * Updated insurance value
     */
    @DecimalMin(value = "0.0", message = "{insurance.value.non-negative}")
    private BigDecimal insuranceValue;
    
    /**
     * Order items to update
     */
    @Valid
    private List<UpdateOrderItemDto> orderItems;
    
    /**
     * Reason for the update (for audit purposes)
     */
    @Size(max = 500, message = "{update.reason.size}")
    private String updateReason;
    
    /**
     * User ID who is making the update
     */
    @Positive(message = "{updated.by.user.id.positive}")
    private Long updatedByUserId;
    
    /**
     * Whether to send notification about the update
     */
    @Builder.Default
    private Boolean sendNotification = true;
    
    /**
     * Additional metadata for the update
     */
    private String metadata;
    
    /**
     * Update Order Item DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateOrderItemDto {
        
        /**
         * ID of the order item to update
         */
        @NotNull(message = "{order.item.id.required}")
        @Positive(message = "{order.item.id.positive}")
        private Long orderItemId;
        
        /**
         * Updated quantity
         */
        @Positive(message = "{order.item.quantity.positive}")
        private Integer quantity;
        
        /**
         * Updated unit price
         */
        @DecimalMin(value = "0.0", message = "{order.item.price.non-negative}")
        private BigDecimal unitPrice;
        
        /**
         * Updated discount amount
         */
        @DecimalMin(value = "0.0", message = "{order.item.discount.non-negative}")
        private BigDecimal discountAmount;
        
        /**
         * Updated special instructions
         */
        @Size(max = 1000, message = "{order.item.instructions.size}")
        private String specialInstructions;
        
        /**
         * Updated gift status for this item
         */
        private Boolean isGift;
        
        /**
         * Updated gift wrap type
         */
        @Size(max = 100, message = "{order.item.gift.wrap.size}")
        private String giftWrapType;
        
        /**
         * Updated gift message for this item
         */
        @Size(max = 500, message = "{order.item.gift.message.size}")
        private String giftMessage;
        
        /**
         * Updated expected delivery date for this item
         */
        private LocalDateTime expectedDeliveryDate;
        
        /**
         * Action to perform on this item (UPDATE, REMOVE)
         */
        @NotBlank(message = "{order.item.action.required}")
        @Pattern(regexp = "^(UPDATE|REMOVE)$", message = "{order.item.action.format}")
        private String action;
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
