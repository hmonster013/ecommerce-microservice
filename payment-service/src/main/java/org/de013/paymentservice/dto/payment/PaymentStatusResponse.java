package org.de013.paymentservice.dto.payment;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.de013.paymentservice.entity.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for payment status information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusResponse {

    private Long paymentId;
    private String paymentNumber;
    private PaymentStatus status;
    private String statusDisplayName;
    private BigDecimal amount;
    private String currency;

    // Stripe-specific status information
    private String stripePaymentIntentId;
    private String stripeStatus;
    private String clientSecret; // For frontend actions

    // Status details
    private String message;
    private String failureReason;
    private Boolean requiresAction;
    private String nextActionType;
    private String nextActionUrl;

    // Processing information
    private Boolean isProcessing;
    private Boolean isCompleted;
    private Boolean isFailed;
    private Boolean isCanceled;

    // Timestamps
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime statusUpdatedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // Additional context
    private String lastTransactionType;
    private String lastTransactionStatus;

    // Helper methods
    @JsonIgnore
    public boolean canRetry() {
        return status == PaymentStatus.FAILED ||
               status == PaymentStatus.REQUIRES_PAYMENT_METHOD;
    }

    @JsonIgnore
    public boolean needsUserAction() {
        return status == PaymentStatus.REQUIRES_ACTION ||
               status == PaymentStatus.REQUIRES_CONFIRMATION;
    }

    @JsonIgnore
    public boolean isInProgress() {
        return status == PaymentStatus.PENDING ||
               status == PaymentStatus.PROCESSING ||
               needsUserAction();
    }

    @JsonIgnore
    public boolean isFinalStatus() {
        return status == PaymentStatus.SUCCEEDED ||
               status == PaymentStatus.FAILED ||
               status == PaymentStatus.CANCELED;
    }

    @JsonIgnore
    public String getStatusMessage() {
        if (message != null && !message.trim().isEmpty()) {
            return message;
        }

        return switch (status) {
            case PENDING -> "Payment is being processed";
            case REQUIRES_ACTION -> "Payment requires additional authentication";
            case REQUIRES_CONFIRMATION -> "Payment requires confirmation";
            case REQUIRES_PAYMENT_METHOD -> "Payment requires a valid payment method";
            case SUCCEEDED -> "Payment completed successfully";
            case CANCELED -> "Payment was canceled";
            case FAILED -> failureReason != null ? failureReason : "Payment failed";
            case PROCESSING -> "Payment is being processed";
        };
    }

    // Factory methods for common scenarios
    public static PaymentStatusResponse success(Long paymentId, String paymentNumber, BigDecimal amount, String currency) {
        return PaymentStatusResponse.builder()
                .paymentId(paymentId)
                .paymentNumber(paymentNumber)
                .status(PaymentStatus.SUCCEEDED)
                .statusDisplayName("Succeeded")
                .amount(amount)
                .currency(currency)
                .isCompleted(true)
                .message("Payment completed successfully")
                .statusUpdatedAt(LocalDateTime.now())
                .build();
    }

    public static PaymentStatusResponse requiresAction(Long paymentId, String paymentNumber, String clientSecret, String actionUrl) {
        return PaymentStatusResponse.builder()
                .paymentId(paymentId)
                .paymentNumber(paymentNumber)
                .status(PaymentStatus.REQUIRES_ACTION)
                .statusDisplayName("Requires Action")
                .clientSecret(clientSecret)
                .requiresAction(true)
                .nextActionType("redirect_to_url")
                .nextActionUrl(actionUrl)
                .message("Payment requires additional authentication")
                .statusUpdatedAt(LocalDateTime.now())
                .build();
    }

    public static PaymentStatusResponse failed(Long paymentId, String paymentNumber, String failureReason) {
        return PaymentStatusResponse.builder()
                .paymentId(paymentId)
                .paymentNumber(paymentNumber)
                .status(PaymentStatus.FAILED)
                .statusDisplayName("Failed")
                .failureReason(failureReason)
                .isFailed(true)
                .message(failureReason)
                .statusUpdatedAt(LocalDateTime.now())
                .build();
    }
}
