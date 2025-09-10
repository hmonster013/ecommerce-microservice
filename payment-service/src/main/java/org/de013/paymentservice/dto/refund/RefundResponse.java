package org.de013.paymentservice.dto.refund;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.de013.paymentservice.entity.enums.RefundStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for refund information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResponse {

    private Long id;
    private String refundNumber;
    private Long paymentId;
    private String paymentNumber;
    private Long orderId;
    private BigDecimal amount;
    private String currency;
    private RefundStatus status;
    private String reason;
    private String description;

    // Stripe information
    private String stripeRefundId;
    private String stripeChargeId;
    private String stripePaymentIntentId;

    // Processing details
    private BigDecimal processingFeeRefunded;
    private BigDecimal netRefundAmount;
    private String receiptNumber;

    // Refund metadata
    private String refundType; // FULL or PARTIAL
    private String initiatedBy;
    private String approvedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approvedAt;

    // Timing information
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime processedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime settledAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expectedArrivalDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    // Related payment information
    private PaymentInfo paymentInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentInfo {
        private Long paymentId;
        private String paymentNumber;
        private BigDecimal originalAmount;
        private String paymentStatus;
        private BigDecimal totalRefundedAmount;
        private Boolean isFullyRefunded;
    }

    // Helper methods for display
    @JsonIgnore
    public String getStatusDisplayName() {
        return switch (status) {
            case PENDING -> "Pending";
            case PROCESSING -> "Processing";
            case SUCCEEDED -> "Succeeded";
            case FAILED -> "Failed";
            case CANCELED -> "Canceled";
            case REJECTED -> "Rejected";
            case REQUIRES_ACTION -> "Requires Action";
        };
    }

    @JsonIgnore
    public String getRefundTypeDisplayName() {
        return switch (refundType) {
            case "FULL" -> "Full Refund";
            case "PARTIAL" -> "Partial Refund";
            default -> "Refund";
        };
    }

    @JsonIgnore
    public boolean isSuccessful() {
        return status == RefundStatus.SUCCEEDED;
    }

    @JsonIgnore
    public boolean isFailed() {
        return status == RefundStatus.FAILED;
    }

    @JsonIgnore
    public boolean isPending() {
        return status == RefundStatus.PENDING;
    }

    @JsonIgnore
    public boolean requiresAction() {
        return status == RefundStatus.REQUIRES_ACTION;
    }

    @JsonIgnore
    public boolean isProcessed() {
        return processedAt != null;
    }

    @JsonIgnore
    public boolean isSettled() {
        return settledAt != null;
    }

    @JsonIgnore
    public String getEstimatedArrival() {
        if (expectedArrivalDate == null) {
            return "Unknown";
        }

        LocalDateTime now = LocalDateTime.now();
        if (expectedArrivalDate.isBefore(now)) {
            return "Should have arrived";
        }

        long daysUntilArrival = java.time.Duration.between(now, expectedArrivalDate).toDays();
        if (daysUntilArrival == 0) {
            return "Today";
        } else if (daysUntilArrival == 1) {
            return "Tomorrow";
        } else {
            return daysUntilArrival + " days";
        }
    }

    @JsonIgnore
    public String getProcessingTimeDescription() {
        if (!isSuccessful()) {
            return "N/A";
        }

        if (settledAt != null) {
            return "Completed";
        }

        if (processedAt != null) {
            return "Processing";
        }

        return "Pending";
    }

    // Factory methods for common scenarios
    public static RefundResponse success(String refundNumber, BigDecimal amount, String currency) {
        return RefundResponse.builder()
                .refundNumber(refundNumber)
                .amount(amount)
                .currency(currency)
                .status(RefundStatus.SUCCEEDED)
                .processedAt(LocalDateTime.now())
                .build();
    }

    public static RefundResponse pending(String refundNumber, BigDecimal amount, String currency) {
        return RefundResponse.builder()
                .refundNumber(refundNumber)
                .amount(amount)
                .currency(currency)
                .status(RefundStatus.PENDING)
                .build();
    }
}
