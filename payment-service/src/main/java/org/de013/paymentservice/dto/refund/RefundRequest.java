package org.de013.paymentservice.dto.refund;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for creating a refund
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {

    @NotNull(message = "Payment ID is required")
    @Positive(message = "Payment ID must be positive")
    private Long paymentId;

    @NotNull(message = "Refund amount is required")
    @DecimalMin(value = "0.01", message = "Refund amount must be greater than 0")
    @Digits(integer = 17, fraction = 2, message = "Refund amount must have at most 2 decimal places")
    private BigDecimal amount;

    @NotBlank(message = "Refund reason is required")
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    // Refund options
    @Builder.Default
    private Boolean reverseTransfer = false; // For Stripe Connect

    @Builder.Default
    private Boolean refundApplicationFee = false; // For Stripe Connect

    // Metadata
    @Size(max = 100, message = "Initiated by must not exceed 100 characters")
    private String initiatedBy;

    // Refund type (will be determined automatically)
    private String refundType; // FULL or PARTIAL

    // Instructions for specific refund scenarios
    @Size(max = 1000, message = "Instructions must not exceed 1000 characters")
    private String instructions;

    // Helper methods
    @JsonIgnore
    public boolean isFullRefund() {
        return "FULL".equals(refundType);
    }

    @JsonIgnore
    public boolean isPartialRefund() {
        return "PARTIAL".equals(refundType);
    }

    @JsonIgnore
    public boolean hasInstructions() {
        return instructions != null && !instructions.trim().isEmpty();
    }

    @JsonIgnore
    public String getReasonCode() {
        // Map common reasons to codes for better categorization
        if (reason == null) return "OTHER";
        
        String lowerReason = reason.toLowerCase();
        if (lowerReason.contains("duplicate")) return "DUPLICATE";
        if (lowerReason.contains("fraudulent")) return "FRAUDULENT";
        if (lowerReason.contains("requested_by_customer")) return "REQUESTED_BY_CUSTOMER";
        if (lowerReason.contains("defective") || lowerReason.contains("damaged")) return "DEFECTIVE_PRODUCT";
        if (lowerReason.contains("not_received") || lowerReason.contains("delivery")) return "NOT_RECEIVED";
        if (lowerReason.contains("cancel")) return "CANCELLATION";
        if (lowerReason.contains("return")) return "RETURN";
        
        return "OTHER";
    }
}
