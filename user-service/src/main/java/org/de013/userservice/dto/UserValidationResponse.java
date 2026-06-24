package org.de013.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for user validation for payment processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserValidationResponse {

    private boolean valid;
    private String message;
    private List<String> errors;

    // User details
    private String userId;
    private String username;
    private String email;
    private String status;
    private String role;

    // Validation flags
    private boolean userExists;
    private boolean userActive;
    private boolean userBlocked;
    private boolean userDeleted;
    private boolean emailVerified;
    private boolean phoneVerified;

    // Payment validation
    private boolean canMakePayments;
    private String paymentBlockReason;
    private boolean hasPaymentLimits;
    private boolean withinPaymentLimits;

    // Risk assessment
    private String riskLevel;
    private Integer riskScore;
    private boolean requiresVerification;
    private boolean highRiskUser;

    // Limits information
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
    private BigDecimal transactionLimit;
    private BigDecimal remainingDailyLimit;
    private BigDecimal remainingMonthlyLimit;
    private Integer remainingTransactionsToday;

    // Activity information
    private LocalDateTime lastPaymentAt;
    private Integer recentPaymentCount;
    private BigDecimal recentPaymentAmount;

    // Factory methods matching payment-service
    public static UserValidationResponse valid(String userId, String username, String email) {
        return UserValidationResponse.builder()
                .valid(true)
                .userId(userId)
                .username(username)
                .email(email)
                .userExists(true)
                .userActive(true)
                .userBlocked(false)
                .userDeleted(false)
                .canMakePayments(true)
                .highRiskUser(false)
                .requiresVerification(false)
                .riskLevel("LOW")
                .riskScore(10)
                .message("User is valid for payment processing")
                .build();
    }

    public static UserValidationResponse userNotFound(String userId) {
        return UserValidationResponse.builder()
                .valid(false)
                .userId(userId)
                .userExists(false)
                .canMakePayments(false)
                .message("User not found")
                .build();
    }
}
