package org.de013.orderservice.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cancel Order Request DTO
 * 
 * Request object for cancelling an existing order.
 * Contains cancellation details and refund preferences.
 * 
 * @author Development Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelOrderRequest {
    
    /**
     * ID of the order to cancel
     */
    @NotNull(message = "Order ID is required")
    @Positive(message = "Order ID must be positive")
    private Long orderId;
    
    /**
     * Reason for cancellation
     */
    @NotBlank(message = "Cancellation reason is required")
    @Size(max = 500, message = "Cancellation reason must not exceed 500 characters")
    private String reason;
    
    /**
     * Detailed cancellation reason category
     */
    @NotBlank(message = "Reason category is required")
    @Pattern(regexp = "^(CUSTOMER_REQUEST|PAYMENT_FAILED|OUT_OF_STOCK|SHIPPING_ISSUE|FRAUD_DETECTED|SYSTEM_ERROR|BUSINESS_DECISION|OTHER)$", 
             message = "Invalid reason category")
    private String reasonCategory;
    
    /**
     * Whether customer requested refund
     */
    @NotNull(message = "Refund requested flag is required")
    private Boolean refundRequested;
    
    /**
     * Refund method preference
     */
    @Pattern(regexp = "^(ORIGINAL_PAYMENT|STORE_CREDIT|BANK_TRANSFER|CHECK|OTHER)$", 
             message = "Invalid refund method")
    private String refundMethod;
    
    /**
     * Partial refund amount (if not full refund)
     */
    @DecimalMin(value = "0.0", message = "Refund amount must be non-negative")
    private BigDecimal partialRefundAmount;
    
    /**
     * Currency for partial refund
     */
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be uppercase letters")
    private String refundCurrency;
    
    /**
     * Specific items to cancel (for partial cancellation)
     */
    private List<CancelOrderItemDto> itemsToCancel;
    
    /**
     * Whether to restock cancelled items
     */
    @Builder.Default
    private Boolean restockItems = true;
    
    /**
     * Whether to send cancellation notification
     */
    @Builder.Default
    private Boolean sendNotification = true;
    
    /**
     * User ID who is cancelling the order
     */
    @Positive(message = "Cancelled by user ID must be positive")
    private Long cancelledByUserId;
    
    /**
     * Whether this is an admin cancellation
     */
    @Builder.Default
    private Boolean isAdminCancellation = false;
    
    /**
     * Internal notes for the cancellation
     */
    @Size(max = 1000, message = "Internal notes must not exceed 1000 characters")
    private String internalNotes;
    
    /**
     * Customer communication notes
     */
    @Size(max = 1000, message = "Customer notes must not exceed 1000 characters")
    private String customerNotes;
    
    /**
     * Whether to blacklist customer (for fraud cases)
     */
    @Builder.Default
    private Boolean blacklistCustomer = false;
    
    /**
     * Whether to block payment method (for fraud cases)
     */
    @Builder.Default
    private Boolean blockPaymentMethod = false;
    
    /**
     * Compensation offered to customer
     */
    private CompensationDto compensation;
    
    /**
     * Additional metadata for the cancellation
     */
    private String metadata;
    
    /**
     * Cancel Order Item DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CancelOrderItemDto {
        
        /**
         * ID of the order item to cancel
         */
        @NotNull(message = "Order item ID is required")
        @Positive(message = "Order item ID must be positive")
        private Long orderItemId;
        
        /**
         * Quantity to cancel (if partial cancellation)
         */
        @Positive(message = "Cancel quantity must be positive")
        private Integer cancelQuantity;
        
        /**
         * Reason for cancelling this specific item
         */
        @Size(max = 500, message = "Item cancellation reason must not exceed 500 characters")
        private String itemCancelReason;
        
        /**
         * Whether to restock this specific item
         */
        @Builder.Default
        private Boolean restockItem = true;
        
        /**
         * Refund amount for this item
         */
        @DecimalMin(value = "0.0", message = "Item refund amount must be non-negative")
        private BigDecimal itemRefundAmount;
    }
    
    /**
     * Compensation DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CompensationDto {
        
        /**
         * Type of compensation
         */
        @NotBlank(message = "Compensation type is required")
        @Pattern(regexp = "^(STORE_CREDIT|DISCOUNT_COUPON|FREE_SHIPPING|GIFT_CARD|CASH_REFUND|PRODUCT_REPLACEMENT|OTHER)$", 
                 message = "Invalid compensation type")
        private String type;
        
        /**
         * Compensation amount
         */
        @DecimalMin(value = "0.0", message = "Compensation amount must be non-negative")
        private BigDecimal amount;
        
        /**
         * Compensation currency
         */
        @Size(min = 3, max = 3, message = "Currency must be 3 characters")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be uppercase letters")
        private String currency;
        
        /**
         * Compensation description
         */
        @Size(max = 500, message = "Compensation description must not exceed 500 characters")
        private String description;
        
        /**
         * Compensation expiry date
         */
        private java.time.LocalDateTime expiryDate;
        
        /**
         * Compensation code (for coupons, gift cards)
         */
        @Size(max = 50, message = "Compensation code must not exceed 50 characters")
        private String compensationCode;
        
        /**
         * Whether compensation is automatically applied
         */
        @Builder.Default
        private Boolean autoApply = false;
    }
    
    /**
     * Check if this is a full order cancellation
     */
    public boolean isFullCancellation() {
        return itemsToCancel == null || itemsToCancel.isEmpty();
    }
    
    /**
     * Check if this is a partial order cancellation
     */
    public boolean isPartialCancellation() {
        return !isFullCancellation();
    }
    
    /**
     * Check if refund is requested
     */
    public boolean isRefundRequested() {
        return Boolean.TRUE.equals(refundRequested);
    }
    
    /**
     * Check if partial refund is requested
     */
    public boolean isPartialRefund() {
        return partialRefundAmount != null && partialRefundAmount.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * Check if this is a fraud-related cancellation
     */
    public boolean isFraudCancellation() {
        return "FRAUD_DETECTED".equals(reasonCategory) || 
               Boolean.TRUE.equals(blacklistCustomer) || 
               Boolean.TRUE.equals(blockPaymentMethod);
    }
    
    /**
     * Check if compensation is offered
     */
    public boolean hasCompensation() {
        return compensation != null;
    }
    
    /**
     * Get total items to cancel count
     */
    public int getTotalItemsToCancel() {
        if (itemsToCancel == null) {
            return 0;
        }
        return itemsToCancel.size();
    }
    
    /**
     * Get total quantity to cancel
     */
    public int getTotalQuantityToCancel() {
        if (itemsToCancel == null) {
            return 0;
        }
        return itemsToCancel.stream()
                .mapToInt(item -> item.getCancelQuantity() != null ? item.getCancelQuantity() : 1)
                .sum();
    }
    
    /**
     * Check if items should be restocked
     */
    public boolean shouldRestockItems() {
        return Boolean.TRUE.equals(restockItems) && 
               !"FRAUD_DETECTED".equals(reasonCategory);
    }
    
    /**
     * Check if customer should be notified
     */
    public boolean shouldNotifyCustomer() {
        return Boolean.TRUE.equals(sendNotification) && 
               !Boolean.TRUE.equals(isAdminCancellation);
    }
    
    /**
     * Get effective refund method
     */
    public String getEffectiveRefundMethod() {
        if (refundMethod != null) {
            return refundMethod;
        }
        return isRefundRequested() ? "ORIGINAL_PAYMENT" : null;
    }
    
    /**
     * Validate cancellation request
     */
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
            return compensation.getType() != null && 
                   compensation.getAmount() != null && 
                   compensation.getCurrency() != null;
        }
        
        return true;
    }
}
