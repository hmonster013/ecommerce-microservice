package org.de013.paymentservice.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for User data from User Service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String status;
    private String role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
    private LocalDateTime lastPaymentAt;

    // Payment-related fields
    private Integer paymentCount;
    private BigDecimal totalPaymentAmount;
    private String paymentStatus;
    private Boolean canMakePayments;

    // Helper methods
    @JsonIgnore
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    @JsonIgnore
    public boolean isBlocked() {
        return "BLOCKED".equals(status);
    }

    @JsonIgnore
    public boolean isDeleted() {
        return "DELETED".equals(status);
    }

    @JsonIgnore
    public String getFullName() {
        return firstName + " " + lastName;
    }

    @JsonIgnore
    public boolean canProcessPayments() {
        return isActive() && Boolean.TRUE.equals(canMakePayments) && !"BLOCKED".equals(paymentStatus);
    }

    /**
     * Payment limits for the user
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentLimits {
        private BigDecimal dailyLimit;
        private BigDecimal monthlyLimit;
        private BigDecimal transactionLimit;
        private BigDecimal remainingDailyLimit;
        private BigDecimal remainingMonthlyLimit;
        private Integer maxTransactionsPerDay;
        private Integer remainingTransactionsToday;
        private Boolean hasLimits;

        public boolean canProcessAmount(BigDecimal amount) {
            if (!hasLimits) return true;

            return (transactionLimit == null || amount.compareTo(transactionLimit) <= 0) &&
                   (remainingDailyLimit == null || amount.compareTo(remainingDailyLimit) <= 0) &&
                   (remainingMonthlyLimit == null || amount.compareTo(remainingMonthlyLimit) <= 0);
        }

        @JsonIgnore
        public boolean canProcessTransaction() {
            return remainingTransactionsToday == null || remainingTransactionsToday > 0;
        }
    }

    /**
     * Risk assessment for fraud detection
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskAssessment {
        private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
        private Integer riskScore; // 0-100
        private String riskReason;
        private Boolean requiresAdditionalVerification;
        private Boolean allowPayments;
        private LocalDateTime lastAssessmentAt;

        // Risk factors
        private Boolean isNewUser;
        private Boolean hasRecentFailedPayments;
        private Boolean hasUnusualActivity;
        private Boolean isFromHighRiskLocation;
        private Integer recentPaymentCount;
        private BigDecimal recentPaymentAmount;

        @JsonIgnore
        public boolean isHighRisk() {
            return "HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel);
        }

        @JsonIgnore
        public boolean shouldBlockPayment() {
            return !Boolean.TRUE.equals(allowPayments) || "CRITICAL".equals(riskLevel);
        }

        @JsonIgnore
        public boolean needsVerification() {
            return Boolean.TRUE.equals(requiresAdditionalVerification) || isHighRisk();
        }
    }
}
