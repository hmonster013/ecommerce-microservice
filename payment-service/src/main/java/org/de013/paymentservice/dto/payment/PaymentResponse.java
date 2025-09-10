package org.de013.paymentservice.dto.payment;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.de013.paymentservice.entity.enums.Currency;
import org.de013.paymentservice.entity.enums.PaymentMethodType;
import org.de013.paymentservice.entity.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for payment information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private Long id;
    private String paymentNumber;
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private Currency currency;
    private PaymentStatus status;
    private PaymentMethodType method;

    // Stripe information (public fields only)
    private String stripePaymentIntentId;
    private String stripeCustomerId;
    private String clientSecret; // For frontend confirmation

    // Payment details
    private String description;
    private String failureReason;
    private String receiptEmail;

    // Payment method information
    private PaymentMethodInfo paymentMethodInfo;

    // Refund information
    private BigDecimal totalRefundedAmount;
    private Boolean isFullyRefunded;
    private Boolean isPartiallyRefunded;

    // Timestamps
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    // Related data
    private List<PaymentTransactionResponse> transactions;
    private List<RefundSummary> refunds;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentMethodInfo {
        private String type;
        private String brand;
        private String last4;
        private Integer expiryMonth;
        private Integer expiryYear;
        private String country;
        private String funding;
        private String walletType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentTransactionResponse {
        private Long id;
        private String type;
        private BigDecimal amount;
        private String status;
        private String description;
        
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundSummary {
        private Long id;
        private String refundNumber;
        private BigDecimal amount;
        private String status;
        private String reason;
        
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createdAt;
    }

    // Helper methods for frontend
    @JsonIgnore
    public boolean canBeRefunded() {
        return status == PaymentStatus.SUCCEEDED && !isFullyRefunded;
    }

    @JsonIgnore
    public boolean canBeCanceled() {
        return status == PaymentStatus.PENDING || 
               status == PaymentStatus.REQUIRES_ACTION ||
               status == PaymentStatus.REQUIRES_CONFIRMATION ||
               status == PaymentStatus.REQUIRES_PAYMENT_METHOD;
    }

    @JsonIgnore
    public BigDecimal getRefundableAmount() {
        if (totalRefundedAmount == null) {
            return amount;
        }
        return amount.subtract(totalRefundedAmount);
    }

    @JsonIgnore
    public String getStatusDisplayName() {
        return switch (status) {
            case PENDING -> "Pending";
            case REQUIRES_ACTION -> "Requires Action";
            case REQUIRES_CONFIRMATION -> "Requires Confirmation";
            case REQUIRES_PAYMENT_METHOD -> "Requires Payment Method";
            case SUCCEEDED -> "Succeeded";
            case CANCELED -> "Canceled";
            case FAILED -> "Failed";
            case PROCESSING -> "Processing";
        };
    }
}
