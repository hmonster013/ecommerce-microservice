package org.de013.paymentservice.dto.stripe;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * DTO for Stripe refund request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StripeRefundRequest {

    private String paymentIntentId;
    private String chargeId;
    private BigDecimal amount;
    private String reason;
    private String description;
    private Map<String, String> metadata;
    private Boolean reverseTransfer;
    private Boolean refundApplicationFee;

    // Helper methods
    public boolean hasAmount() {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean hasReason() {
        return reason != null && !reason.trim().isEmpty();
    }

    public boolean hasMetadata() {
        return metadata != null && !metadata.isEmpty();
    }
}
