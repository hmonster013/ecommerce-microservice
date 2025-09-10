package org.de013.paymentservice.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for user validation from User Service
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
    private Long userId;
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
    
    // Helper methods
    @JsonIgnore
    public boolean isPaymentAllowed() {
        return valid && canMakePayments && !userBlocked && !highRiskUser;
    }

    public boolean canProcessAmount(BigDecimal amount) {
        if (!hasPaymentLimits) return true;

        return (transactionLimit == null || amount.compareTo(transactionLimit) <= 0) &&
               (remainingDailyLimit == null || amount.compareTo(remainingDailyLimit) <= 0) &&
               (remainingMonthlyLimit == null || amount.compareTo(remainingMonthlyLimit) <= 0);
    }

    @JsonIgnore
    public boolean needsAdditionalVerification() {
        return requiresVerification || highRiskUser || "HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel);
    }

    @JsonIgnore
    public boolean hasPaymentRestrictions() {
        return paymentBlockReason != null && !paymentBlockReason.trim().isEmpty();
    }
    
    // Factory methods
    public static UserValidationResponse valid(Long userId, String username, String email) {
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
    
    public static UserValidationResponse invalid(String message, List<String> errors) {
        return UserValidationResponse.builder()
                .valid(false)
                .message(message)
                .errors(errors)
                .canMakePayments(false)
                .build();
    }
    
    public static UserValidationResponse userNotFound(Long userId) {
        return UserValidationResponse.builder()
                .valid(false)
                .userId(userId)
                .userExists(false)
                .canMakePayments(false)
                .message("User not found")
                .build();
    }
    
    public static UserValidationResponse userBlocked(Long userId, String username, String reason) {
        return UserValidationResponse.builder()
                .valid(false)
                .userId(userId)
                .username(username)
                .userExists(true)
                .userActive(false)
                .userBlocked(true)
                .canMakePayments(false)
                .paymentBlockReason(reason)
                .message("User is blocked from making payments")
                .build();
    }
    
    public static UserValidationResponse highRisk(Long userId, String username, String riskLevel, String reason) {
        return UserValidationResponse.builder()
                .valid(false)
                .userId(userId)
                .username(username)
                .userExists(true)
                .userActive(true)
                .userBlocked(false)
                .canMakePayments(false)
                .highRiskUser(true)
                .requiresVerification(true)
                .riskLevel(riskLevel)
                .riskScore(riskLevel.equals("CRITICAL") ? 95 : 75)
                .paymentBlockReason(reason)
                .message("User requires additional verification due to high risk")
                .build();
    }
}
