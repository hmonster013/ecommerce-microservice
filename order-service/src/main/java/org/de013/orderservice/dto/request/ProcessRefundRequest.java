package org.de013.orderservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Process Refund Request DTO
 * 
 * Request object for processing refunds for orders or order items.
 * Contains refund details and processing preferences.
 * 
 * @author Development Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessRefundRequest {
    
    /**
     * ID of the order to refund
     */
    @NotNull(message = "{order.id.required}")
    @Positive(message = "{order.id.positive}")
    private Long orderId;
    
    /**
     * ID of the payment to refund
     */
    @Positive(message = "{field.positive}")
    private Long paymentId;
    
    /**
     * Refund amount
     */
    @NotNull(message = "{field.required}")
    @DecimalMin(value = "0.01", message = "{refund.amount.positive}")
    private BigDecimal refundAmount;
    
    /**
     * Refund currency
     */
    @NotBlank(message = "{currency.required}")
    @Size(min = 3, max = 3, message = "{currency.size}")
    @Pattern(regexp = "^[A-Z]{3}$", message = "{currency.format}")
    private String refundCurrency;
    
    /**
     * Reason for the refund
     */
    @NotBlank(message = "{refund.reason.required}")
    @Size(max = 1000, message = "{refund.reason.size}")
    private String refundReason;
    
    /**
     * Refund reason category
     */
    @NotBlank(message = "{field.required}")
    @Pattern(regexp = "^(CUSTOMER_REQUEST|DEFECTIVE_PRODUCT|WRONG_ITEM|DAMAGED_SHIPPING|LATE_DELIVERY|CANCELLED_ORDER|DUPLICATE_CHARGE|FRAUD_PROTECTION|GOODWILL|OTHER)$",
             message = "{field.invalid.format}")
    private String refundReasonCategory;
    
    /**
     * Refund method
     */
    @NotBlank(message = "{field.required}")
    @Pattern(regexp = "^(ORIGINAL_PAYMENT|STORE_CREDIT|BANK_TRANSFER|CHECK|GIFT_CARD|OTHER)$",
             message = "{field.invalid.format}")
    private String refundMethod;
    
    /**
     * Specific items to refund (for partial refunds)
     */
    @Valid
    private List<RefundItemDto> refundItems;
    
    /**
     * Whether this is a full order refund
     */
    @Builder.Default
    private Boolean isFullRefund = true;
    
    /**
     * Whether to restock refunded items
     */
    @Builder.Default
    private Boolean restockItems = true;
    
    /**
     * User ID who is processing the refund
     */
    @NotNull(message = "{user.id.required}")
    @Positive(message = "{user.id.positive}")
    private Long processedByUserId;
    
    /**
     * Whether this is an admin-initiated refund
     */
    @Builder.Default
    private Boolean isAdminRefund = false;
    
    /**
     * Internal notes for the refund
     */
    @Size(max = 2000, message = "{internal.notes.size}")
    private String internalNotes;
    
    /**
     * Customer communication notes
     */
    @Size(max = 1000, message = "{customer.notes.size}")
    private String customerNotes;
    
    /**
     * Whether to send refund notification
     */
    @Builder.Default
    private Boolean sendNotification = true;
    
    /**
     * Expected refund processing time in days
     */
    @Min(value = 1, message = "{field.size.range}")
    @Max(value = 30, message = "{field.size.range}")
    @Builder.Default
    private Integer expectedProcessingDays = 5;
    
    /**
     * Refund processing priority
     */
    @Min(value = 1, message = "{priority.level.range}")
    @Max(value = 5, message = "{priority.level.range}")
    @Builder.Default
    private Integer priority = 3;
    
    /**
     * Whether to waive restocking fee
     */
    @Builder.Default
    private Boolean waiveRestockingFee = false;
    
    /**
     * Restocking fee amount (if applicable)
     */
    @DecimalMin(value = "0.0", message = "{field.non-negative}")
    private BigDecimal restockingFee;
    
    /**
     * Return shipping cost to deduct from refund
     */
    @DecimalMin(value = "0.0", message = "{field.non-negative}")
    private BigDecimal returnShippingCost;
    
    /**
     * Additional fees to deduct from refund
     */
    @Valid
    private List<RefundFeeDto> additionalFees;
    
    /**
     * Refund approval information
     */
    @Valid
    private RefundApprovalDto approval;
    
    /**
     * Additional metadata for the refund
     */
    private String metadata;
    
    /**
     * Refund Item DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RefundItemDto {
        
        /**
         * ID of the order item to refund
         */
        @NotNull(message = "{refund.item.id.required}")
        @Positive(message = "{refund.item.id.positive}")
        private Long orderItemId;
        
        /**
         * Quantity to refund
         */
        @NotNull(message = "{field.required}")
        @Positive(message = "{refund.item.quantity.positive}")
        private Integer refundQuantity;
        
        /**
         * Refund amount for this item
         */
        @NotNull(message = "{field.required}")
        @DecimalMin(value = "0.01", message = "{refund.amount.positive}")
        private BigDecimal itemRefundAmount;
        
        /**
         * Reason for refunding this specific item
         */
        @Size(max = 500, message = "{refund.reason.size}")
        private String itemRefundReason;
        
        /**
         * Whether to restock this specific item
         */
        @Builder.Default
        private Boolean restockItem = true;
        
        /**
         * Condition of returned item
         */
        @Pattern(regexp = "^(NEW|LIKE_NEW|GOOD|FAIR|POOR|DAMAGED|DEFECTIVE)$",
                 message = "{field.invalid.format}")
        private String itemCondition;
        
        /**
         * Item inspection notes
         */
        @Size(max = 1000, message = "{internal.notes.size}")
        private String inspectionNotes;
    }
    
    /**
     * Refund Fee DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RefundFeeDto {
        
        /**
         * Fee type
         */
        @NotBlank(message = "{field.required}")
        @Pattern(regexp = "^(RESTOCKING|RETURN_SHIPPING|PROCESSING|HANDLING|INSPECTION|OTHER)$",
                 message = "{field.invalid.format}")
        private String feeType;
        
        /**
         * Fee amount
         */
        @NotNull(message = "{field.required}")
        @DecimalMin(value = "0.0", message = "{field.non-negative}")
        private BigDecimal feeAmount;
        
        /**
         * Fee description
         */
        @Size(max = 200, message = "{field.size.max}")
        private String feeDescription;
        
        /**
         * Whether fee is waived
         */
        @Builder.Default
        private Boolean isWaived = false;
    }
    
    /**
     * Refund Approval DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RefundApprovalDto {
        
        /**
         * Whether approval is required
         */
        @Builder.Default
        private Boolean approvalRequired = false;
        
        /**
         * Approval status
         */
        @Pattern(regexp = "^(PENDING|APPROVED|REJECTED|AUTO_APPROVED)$",
                 message = "{field.invalid.format}")
        private String approvalStatus;
        
        /**
         * User ID who approved the refund
         */
        @Positive(message = "{user.id.positive}")
        private Long approvedByUserId;
        
        /**
         * Approval timestamp
         */
        private LocalDateTime approvedAt;
        
        /**
         * Approval notes
         */
        @Size(max = 1000, message = "{internal.notes.size}")
        private String approvalNotes;
        
        /**
         * Approval threshold amount
         */
        @DecimalMin(value = "0.0", message = "{field.non-negative}")
        private BigDecimal approvalThreshold;
    }
    
    /**
     * Calculate net refund amount after fees
     */
    public BigDecimal getNetRefundAmount() {
        BigDecimal netAmount = refundAmount;
        
        // Subtract restocking fee if not waived
        if (!Boolean.TRUE.equals(waiveRestockingFee) && restockingFee != null) {
            netAmount = netAmount.subtract(restockingFee);
        }
        
        // Subtract return shipping cost
        if (returnShippingCost != null) {
            netAmount = netAmount.subtract(returnShippingCost);
        }
        
        // Subtract additional fees
        if (additionalFees != null) {
            BigDecimal totalFees = additionalFees.stream()
                    .filter(fee -> !Boolean.TRUE.equals(fee.getIsWaived()))
                    .map(RefundFeeDto::getFeeAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            netAmount = netAmount.subtract(totalFees);
        }
        
        return netAmount.max(BigDecimal.ZERO);
    }
    
    /**
     * Check if this is a partial refund
     */
    public boolean isPartialRefund() {
        return !Boolean.TRUE.equals(isFullRefund) || 
               (refundItems != null && !refundItems.isEmpty());
    }
    
    /**
     * Check if approval is required
     */
    public boolean requiresApproval() {
        return approval != null && Boolean.TRUE.equals(approval.getApprovalRequired());
    }
    
    /**
     * Check if refund is approved
     */
    public boolean isApproved() {
        return approval != null && "APPROVED".equals(approval.getApprovalStatus());
    }
    
    /**
     * Check if items should be restocked
     */
    public boolean shouldRestockItems() {
        return Boolean.TRUE.equals(restockItems) && 
               !"DEFECTIVE_PRODUCT".equals(refundReasonCategory) &&
               !"DAMAGED_SHIPPING".equals(refundReasonCategory);
    }
    
    /**
     * Get total quantity to refund
     */
    public int getTotalRefundQuantity() {
        if (refundItems == null) {
            return 0;
        }
        return refundItems.stream()
                .mapToInt(RefundItemDto::getRefundQuantity)
                .sum();
    }
    
    /**
     * Get total items to refund count
     */
    public int getTotalRefundItems() {
        return refundItems != null ? refundItems.size() : 0;
    }
    
    /**
     * Check if customer should be notified
     */
    public boolean shouldNotifyCustomer() {
        return Boolean.TRUE.equals(sendNotification);
    }
    
    /**
     * Get expected refund completion date
     */
    public LocalDateTime getExpectedCompletionDate() {
        return LocalDateTime.now().plusDays(expectedProcessingDays);
    }
    
    /**
     * Validate refund request
     */
    public boolean isValid() {
        // Net refund amount should be positive
        if (getNetRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        // If partial refund, items should be specified
        if (isPartialRefund() && (refundItems == null || refundItems.isEmpty())) {
            return false;
        }
        
        // If approval required, approval info should be provided
        if (requiresApproval() && approval.getApprovalStatus() == null) {
            return false;
        }
        
        return true;
    }
}
