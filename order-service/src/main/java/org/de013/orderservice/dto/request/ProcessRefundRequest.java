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
    @NotNull(message = "Order ID is required")
    @Positive(message = "Order ID must be positive")
    private Long orderId;
    
    /**
     * ID of the payment to refund
     */
    @Positive(message = "Payment ID must be positive")
    private Long paymentId;
    
    /**
     * Refund amount
     */
    @NotNull(message = "Refund amount is required")
    @DecimalMin(value = "0.01", message = "Refund amount must be greater than 0")
    private BigDecimal refundAmount;
    
    /**
     * Refund currency
     */
    @NotBlank(message = "Refund currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be uppercase letters")
    private String refundCurrency;
    
    /**
     * Reason for the refund
     */
    @NotBlank(message = "Refund reason is required")
    @Size(max = 1000, message = "Refund reason must not exceed 1000 characters")
    private String refundReason;
    
    /**
     * Refund reason category
     */
    @NotBlank(message = "Refund reason category is required")
    @Pattern(regexp = "^(CUSTOMER_REQUEST|DEFECTIVE_PRODUCT|WRONG_ITEM|DAMAGED_SHIPPING|LATE_DELIVERY|CANCELLED_ORDER|DUPLICATE_CHARGE|FRAUD_PROTECTION|GOODWILL|OTHER)$", 
             message = "Invalid refund reason category")
    private String refundReasonCategory;
    
    /**
     * Refund method
     */
    @NotBlank(message = "Refund method is required")
    @Pattern(regexp = "^(ORIGINAL_PAYMENT|STORE_CREDIT|BANK_TRANSFER|CHECK|GIFT_CARD|OTHER)$", 
             message = "Invalid refund method")
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
    @NotNull(message = "Processed by user ID is required")
    @Positive(message = "Processed by user ID must be positive")
    private Long processedByUserId;
    
    /**
     * Whether this is an admin-initiated refund
     */
    @Builder.Default
    private Boolean isAdminRefund = false;
    
    /**
     * Internal notes for the refund
     */
    @Size(max = 2000, message = "Internal notes must not exceed 2000 characters")
    private String internalNotes;
    
    /**
     * Customer communication notes
     */
    @Size(max = 1000, message = "Customer notes must not exceed 1000 characters")
    private String customerNotes;
    
    /**
     * Whether to send refund notification
     */
    @Builder.Default
    private Boolean sendNotification = true;
    
    /**
     * Expected refund processing time in days
     */
    @Min(value = 1, message = "Processing time must be at least 1 day")
    @Max(value = 30, message = "Processing time must not exceed 30 days")
    @Builder.Default
    private Integer expectedProcessingDays = 5;
    
    /**
     * Refund processing priority
     */
    @Min(value = 1, message = "Priority must be at least 1")
    @Max(value = 5, message = "Priority must not exceed 5")
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
    @DecimalMin(value = "0.0", message = "Restocking fee must be non-negative")
    private BigDecimal restockingFee;
    
    /**
     * Return shipping cost to deduct from refund
     */
    @DecimalMin(value = "0.0", message = "Return shipping cost must be non-negative")
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
        @NotNull(message = "Order item ID is required")
        @Positive(message = "Order item ID must be positive")
        private Long orderItemId;
        
        /**
         * Quantity to refund
         */
        @NotNull(message = "Refund quantity is required")
        @Positive(message = "Refund quantity must be positive")
        private Integer refundQuantity;
        
        /**
         * Refund amount for this item
         */
        @NotNull(message = "Item refund amount is required")
        @DecimalMin(value = "0.01", message = "Item refund amount must be greater than 0")
        private BigDecimal itemRefundAmount;
        
        /**
         * Reason for refunding this specific item
         */
        @Size(max = 500, message = "Item refund reason must not exceed 500 characters")
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
                 message = "Invalid item condition")
        private String itemCondition;
        
        /**
         * Item inspection notes
         */
        @Size(max = 1000, message = "Inspection notes must not exceed 1000 characters")
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
        @NotBlank(message = "Fee type is required")
        @Pattern(regexp = "^(RESTOCKING|RETURN_SHIPPING|PROCESSING|HANDLING|INSPECTION|OTHER)$", 
                 message = "Invalid fee type")
        private String feeType;
        
        /**
         * Fee amount
         */
        @NotNull(message = "Fee amount is required")
        @DecimalMin(value = "0.0", message = "Fee amount must be non-negative")
        private BigDecimal feeAmount;
        
        /**
         * Fee description
         */
        @Size(max = 200, message = "Fee description must not exceed 200 characters")
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
                 message = "Invalid approval status")
        private String approvalStatus;
        
        /**
         * User ID who approved the refund
         */
        @Positive(message = "Approved by user ID must be positive")
        private Long approvedByUserId;
        
        /**
         * Approval timestamp
         */
        private LocalDateTime approvedAt;
        
        /**
         * Approval notes
         */
        @Size(max = 1000, message = "Approval notes must not exceed 1000 characters")
        private String approvalNotes;
        
        /**
         * Approval threshold amount
         */
        @DecimalMin(value = "0.0", message = "Approval threshold must be non-negative")
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
