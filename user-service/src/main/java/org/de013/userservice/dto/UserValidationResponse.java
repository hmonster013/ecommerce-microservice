package org.de013.userservice.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for user validation for payment processing
 */
@Builder
public record UserValidationResponse(
        boolean valid,
        String message,
        List<String> errors,

        // User details
        String userId,
        String username,
        String email,
        String status,
        String role,

        // Validation flags
        boolean userExists,
        boolean userActive,
        boolean userBlocked,
        boolean userDeleted,
        boolean emailVerified,
        boolean phoneVerified,

        // Payment validation
        boolean canMakePayments,
        String paymentBlockReason,
        boolean hasPaymentLimits,
        boolean withinPaymentLimits,

        // Risk assessment
        String riskLevel,
        Integer riskScore,
        boolean requiresVerification,
        boolean highRiskUser,

        // Limits information
        BigDecimal dailyLimit,
        BigDecimal monthlyLimit,
        BigDecimal transactionLimit,
        BigDecimal remainingDailyLimit,
        BigDecimal remainingMonthlyLimit,
        Integer remainingTransactionsToday,

        // Activity information
        LocalDateTime lastPaymentAt,
        Integer recentPaymentCount,
        BigDecimal recentPaymentAmount
) {

    // Factory methods matching payment-service
    public static UserValidationResponse valid(String userId, String username, String email) {
        return UserValidationResponse.builder()
                .valid(true)
                .message("User is valid for payment processing")
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
                .build();
    }

    public static UserValidationResponse userNotFound(String userId) {
        return UserValidationResponse.builder()
                .valid(false)
                .message("User not found")
                .userId(userId)
                .userExists(false)
                .canMakePayments(false)
                .build();
    }
}
