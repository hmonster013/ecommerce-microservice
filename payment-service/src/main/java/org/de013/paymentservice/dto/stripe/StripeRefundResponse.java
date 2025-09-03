package org.de013.paymentservice.dto.stripe;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for Stripe refund response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StripeRefundResponse {

    private String refundId;
    private String chargeId;
    private String paymentIntentId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String reason;
    private String description;
    private String failureReason;
    private String failureBalanceTransaction;
    private Map<String, String> metadata;
    private LocalDateTime created;
    private LocalDateTime processedAt;
    private LocalDateTime expectedArrivalDate;
    private Boolean livemode;

    // Helper methods
    public boolean isSuccessful() {
        return "succeeded".equals(status);
    }

    public boolean isPending() {
        return "pending".equals(status);
    }

    public boolean isFailed() {
        return "failed".equals(status);
    }

    public boolean isCanceled() {
        return "canceled".equals(status);
    }

    public boolean hasFailureReason() {
        return failureReason != null && !failureReason.trim().isEmpty();
    }

    public boolean isProcessed() {
        return processedAt != null;
    }

    public boolean hasExpectedArrival() {
        return expectedArrivalDate != null;
    }
}
