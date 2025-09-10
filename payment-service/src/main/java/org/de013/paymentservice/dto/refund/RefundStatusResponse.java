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
 * Response DTO for refund status information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundStatusResponse {

    private Long refundId;
    private String refundNumber;
    private RefundStatus status;
    private String statusDisplayName;
    private BigDecimal amount;
    private String currency;

    // Stripe-specific status information
    private String stripeRefundId;
    private String stripeStatus;

    // Status details
    private String message;
    private String failureReason;
    private Boolean requiresAction;
    private String nextActionType;

    // Processing information
    private Boolean isProcessing;
    private Boolean isCompleted;
    private Boolean isFailed;
    private Boolean isCanceled;

    // Timing information
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime statusUpdatedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime processedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expectedArrivalDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // Related payment information
    private Long paymentId;
    private String paymentNumber;

    // Helper methods
    @JsonIgnore
    public boolean canBeRetried() {
        return status == RefundStatus.FAILED;
    }

    @JsonIgnore
    public boolean needsAction() {
        return status == RefundStatus.REQUIRES_ACTION;
    }

    @JsonIgnore
    public boolean isInProgress() {
        return status == RefundStatus.PENDING || needsAction();
    }

    @JsonIgnore
    public boolean isFinalStatus() {
        return status == RefundStatus.SUCCEEDED ||
               status == RefundStatus.FAILED ||
               status == RefundStatus.CANCELED;
    }

    @JsonIgnore
    public String getStatusMessage() {
        if (message != null && !message.trim().isEmpty()) {
            return message;
        }

        return switch (status) {
            case PENDING -> "Refund is being processed";
            case PROCESSING -> "Refund is currently processing";
            case SUCCEEDED -> "Refund completed successfully";
            case FAILED -> failureReason != null ? failureReason : "Refund failed";
            case CANCELED -> "Refund was canceled";
            case REJECTED -> "Refund was rejected";
            case REQUIRES_ACTION -> "Refund requires additional action";
        };
    }

    @JsonIgnore
    public String getEstimatedArrivalMessage() {
        if (status != RefundStatus.SUCCEEDED || expectedArrivalDate == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        if (expectedArrivalDate.isBefore(now)) {
            return "Refund should have been received";
        }

        long daysUntilArrival = java.time.Duration.between(now, expectedArrivalDate).toDays();
        if (daysUntilArrival == 0) {
            return "Refund should arrive today";
        } else if (daysUntilArrival == 1) {
            return "Refund should arrive tomorrow";
        } else {
            return "Refund should arrive in " + daysUntilArrival + " days";
        }
    }

    // Factory methods for common scenarios
    public static RefundStatusResponse success(Long refundId, String refundNumber, BigDecimal amount, String currency) {
        return RefundStatusResponse.builder()
                .refundId(refundId)
                .refundNumber(refundNumber)
                .status(RefundStatus.SUCCEEDED)
                .statusDisplayName("Succeeded")
                .amount(amount)
                .currency(currency)
                .isCompleted(true)
                .message("Refund completed successfully")
                .statusUpdatedAt(LocalDateTime.now())
                .processedAt(LocalDateTime.now())
                .expectedArrivalDate(LocalDateTime.now().plusDays(5)) // Typical bank processing time
                .build();
    }

    public static RefundStatusResponse pending(Long refundId, String refundNumber, BigDecimal amount, String currency) {
        return RefundStatusResponse.builder()
                .refundId(refundId)
                .refundNumber(refundNumber)
                .status(RefundStatus.PENDING)
                .statusDisplayName("Pending")
                .amount(amount)
                .currency(currency)
                .isProcessing(true)
                .message("Refund is being processed")
                .statusUpdatedAt(LocalDateTime.now())
                .build();
    }

    public static RefundStatusResponse failed(Long refundId, String refundNumber, String failureReason) {
        return RefundStatusResponse.builder()
                .refundId(refundId)
                .refundNumber(refundNumber)
                .status(RefundStatus.FAILED)
                .statusDisplayName("Failed")
                .failureReason(failureReason)
                .isFailed(true)
                .message(failureReason)
                .statusUpdatedAt(LocalDateTime.now())
                .build();
    }
}
