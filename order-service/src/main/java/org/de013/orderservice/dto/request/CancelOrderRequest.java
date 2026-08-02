package org.de013.orderservice.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cancel Order Request DTO
 */
public record CancelOrderRequest(
        // ID of the order to cancel
        @NotNull(message = "{order.id.required}")
        @Positive(message = "{order.id.positive}")
        Long orderId,

        // Reason for cancellation
        @NotBlank(message = "{cancellation.reason.required}")
        @Size(max = 500, message = "{cancellation.reason.size}")
        String reason,

        // Detailed cancellation reason category
        @NotBlank(message = "{field.required}")
        @Pattern(regexp = "^(CUSTOMER_REQUEST|PAYMENT_FAILED|OUT_OF_STOCK|SHIPPING_ISSUE|FRAUD_DETECTED|SYSTEM_ERROR|BUSINESS_DECISION|OTHER)$",
                message = "{field.invalid.format}")
        String reasonCategory,

        // Whether customer requested refund
        @NotNull(message = "{field.required}")
        Boolean refundRequested,

        // Refund method preference
        @Pattern(regexp = "^(ORIGINAL_PAYMENT|STORE_CREDIT|BANK_TRANSFER|CHECK|OTHER)$",
                message = "{field.invalid.format}")
        String refundMethod,

        // Partial refund amount (if not full refund)
        @DecimalMin(value = "0.0", message = "{field.non-negative}")
        BigDecimal partialRefundAmount,

        // Currency for partial refund
        @Size(min = 3, max = 3, message = "{currency.size}")
        @Pattern(regexp = "^[A-Z]{3}$", message = "{currency.format}")
        String refundCurrency,

        // Specific items to cancel (for partial cancellation)
        List<CancelOrderItemDto> itemsToCancel,

        // Whether to restock cancelled items
        Boolean restockItems,

        // Whether to send cancellation notification
        Boolean sendNotification,

        // User ID who is cancelling the order
        @Positive(message = "{user.id.positive}")
        Long cancelledByUserId,

        // Whether this is an admin cancellation
        Boolean isAdminCancellation,

        // Internal notes for the cancellation
        @Size(max = 1000, message = "{internal.notes.size}")
        String internalNotes,

        // Customer communication notes
        @Size(max = 1000, message = "{customer.notes.size}")
        String customerNotes,

        // Whether to blacklist customer (for fraud cases)
        Boolean blacklistCustomer,

        // Whether to block payment method (for fraud cases)
        Boolean blockPaymentMethod,

        // Compensation offered to customer
        CompensationDto compensation,

        // Additional metadata for the cancellation
        String metadata
) {

    public CancelOrderRequest {
        if (restockItems == null) {
            restockItems = true;
        }
        if (sendNotification == null) {
            sendNotification = true;
        }
        if (isAdminCancellation == null) {
            isAdminCancellation = false;
        }
        if (blacklistCustomer == null) {
            blacklistCustomer = false;
        }
        if (blockPaymentMethod == null) {
            blockPaymentMethod = false;
        }
    }

    /**
     * Cancel Order Item DTO
     */
    public record CancelOrderItemDto(
            // ID of the order item to cancel
            @NotNull(message = "{order.item.id.required}")
            @Positive(message = "{order.item.id.positive}")
            Long orderItemId,

            // Quantity to cancel (if partial cancellation)
            @Positive(message = "{order.item.quantity.positive}")
            Integer cancelQuantity,

            // Reason for cancelling this specific item
            @Size(max = 500, message = "{cancellation.reason.size}")
            String itemCancelReason,

            // Whether to restock this specific item
            Boolean restockItem,

            // Refund amount for this item
            @DecimalMin(value = "0.0", message = "{field.non-negative}")
            BigDecimal itemRefundAmount
    ) {

        public CancelOrderItemDto {
            if (restockItem == null) {
                restockItem = true;
            }
        }
    }

    /**
     * Compensation DTO
     */
    public record CompensationDto(
            // Type of compensation
            @NotBlank(message = "{field.required}")
            @Pattern(regexp = "^(STORE_CREDIT|DISCOUNT_COUPON|FREE_SHIPPING|GIFT_CARD|CASH_REFUND|PRODUCT_REPLACEMENT|OTHER)$",
                    message = "{field.invalid.format}")
            String type,

            // Compensation amount
            @DecimalMin(value = "0.0", message = "{field.non-negative}")
            BigDecimal amount,

            // Compensation currency
            @Size(min = 3, max = 3, message = "{currency.size}")
            @Pattern(regexp = "^[A-Z]{3}$", message = "{currency.format}")
            String currency,

            // Compensation description
            @Size(max = 500, message = "{field.size.max}")
            String description,

            // Compensation expiry date
            LocalDateTime expiryDate,

            // Compensation code (for coupons, gift cards)
            @Size(max = 50, message = "{field.size.max}")
            String compensationCode,

            // Whether compensation is automatically applied
            Boolean autoApply
    ) {

        public CompensationDto {
            if (autoApply == null) {
                autoApply = false;
            }
        }
    }

    /**
     * Check if this is a full order cancellation
     */
    @JsonIgnore
    public boolean isFullCancellation() {
        return itemsToCancel == null || itemsToCancel.isEmpty();
    }

    /**
     * Check if this is a partial order cancellation
     */
    @JsonIgnore
    public boolean isPartialCancellation() {
        return !isFullCancellation();
    }

    /**
     * Check if refund is requested
     */
    @JsonIgnore
    public boolean isRefundRequested() {
        return Boolean.TRUE.equals(refundRequested);
    }

    /**
     * Check if partial refund is requested
     */
    @JsonIgnore
    public boolean isPartialRefund() {
        return partialRefundAmount != null && partialRefundAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Check if this is a fraud-related cancellation
     */
    @JsonIgnore
    public boolean isFraudCancellation() {
        return "FRAUD_DETECTED".equals(reasonCategory) ||
                Boolean.TRUE.equals(blacklistCustomer) ||
                Boolean.TRUE.equals(blockPaymentMethod);
    }

    /**
     * Check if compensation is offered
     */
    @JsonIgnore
    public boolean hasCompensation() {
        return compensation != null;
    }

    /**
     * Get total items to cancel count
     */
    @JsonIgnore
    public int getTotalItemsToCancel() {
        if (itemsToCancel == null) {
            return 0;
        }
        return itemsToCancel.size();
    }

    /**
     * Get total quantity to cancel
     */
    @JsonIgnore
    public int getTotalQuantityToCancel() {
        if (itemsToCancel == null) {
            return 0;
        }
        return itemsToCancel.stream()
                .mapToInt(item -> item.cancelQuantity() != null ? item.cancelQuantity() : 1)
                .sum();
    }

    /**
     * Check if items should be restocked
     */
    @JsonIgnore
    public boolean shouldRestockItems() {
        return Boolean.TRUE.equals(restockItems) &&
                !"FRAUD_DETECTED".equals(reasonCategory);
    }

    /**
     * Check if customer should be notified
     */
    @JsonIgnore
    public boolean shouldNotifyCustomer() {
        return Boolean.TRUE.equals(sendNotification) &&
                !Boolean.TRUE.equals(isAdminCancellation);
    }

    /**
     * Get effective refund method
     */
    @JsonIgnore
    public String getEffectiveRefundMethod() {
        if (refundMethod != null) {
            return refundMethod;
        }
        return isRefundRequested() ? "ORIGINAL_PAYMENT" : null;
    }

    /**
     * Validate cancellation request
     */
    @JsonIgnore
    public boolean isValid() {
        // If refund is requested, refund method should be specified
        if (isRefundRequested() && refundMethod == null) {
            return false;
        }

        // If partial refund, amount and currency should be specified
        if (isPartialRefund() && refundCurrency == null) {
            return false;
        }

        // If compensation is offered, it should be valid
        if (hasCompensation()) {
            return compensation.type() != null &&
                    compensation.amount() != null &&
                    compensation.currency() != null;
        }

        return true;
    }
}
