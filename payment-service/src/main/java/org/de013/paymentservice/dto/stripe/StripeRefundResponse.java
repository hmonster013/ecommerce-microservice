package org.de013.paymentservice.dto.stripe;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    @JsonIgnore
    public boolean isSuccessful() {
        return "succeeded".equals(status);
    }

    @JsonIgnore
    public boolean isPending() {
        return "pending".equals(status);
    }

    @JsonIgnore
    public boolean isFailed() {
        return "failed".equals(status);
    }

    @JsonIgnore
    public boolean isCanceled() {
        return "canceled".equals(status);
    }

    @JsonIgnore
    public boolean hasFailureReason() {
        return failureReason != null && !failureReason.trim().isEmpty();
    }

    @JsonIgnore
    public boolean isProcessed() {
        return processedAt != null;
    }

    @JsonIgnore
    public boolean hasExpectedArrival() {
        return expectedArrivalDate != null;
    }
}
