package org.de013.paymentservice.client;

import lombok.extern.slf4j.Slf4j;
import org.de013.paymentservice.dto.external.UserDto;
import org.de013.paymentservice.dto.external.UserValidationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Fallback implementation for UserServiceClient
 * Provides default responses when User Service is unavailable
 */
@Slf4j
@Component
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public ResponseEntity<UserDto> getUserById(Long userId) {
        log.warn("User Service unavailable - using fallback for getUserById: {}", userId);
        return ResponseEntity.ok(createFallbackUserDto(userId));
    }

    @Override
    public ResponseEntity<UserDto> getUserByEmail(String email) {
        log.warn("User Service unavailable - using fallback for getUserByEmail: {}", email);
        UserDto fallbackUser = createFallbackUserDto(null);
        fallbackUser.setEmail(email);
        return ResponseEntity.ok(fallbackUser);
    }

    @Override
    public ResponseEntity<UserValidationResponse> validateUserForPayment(Long userId) {
        log.warn("User Service unavailable - using fallback validation for user: {}", userId);
        return ResponseEntity.ok(UserValidationResponse.invalid(
            "User Service unavailable - cannot validate user",
            List.of("Service temporarily unavailable", "Please try again later")
        ));
    }

    @Override
    public ResponseEntity<Boolean> userExists(Long userId) {
        log.warn("User Service unavailable - using fallback for userExists: {}", userId);
        return ResponseEntity.ok(false);
    }

    @Override
    public ResponseEntity<Boolean> isUserActive(Long userId) {
        log.warn("User Service unavailable - using fallback for isUserActive: {}", userId);
        return ResponseEntity.ok(false);
    }

    @Override
    public ResponseEntity<Boolean> canUserMakePayments(Long userId) {
        log.warn("User Service unavailable - using fallback for canUserMakePayments: {}", userId);
        return ResponseEntity.ok(false);
    }

    @Override
    public ResponseEntity<UserDto.PaymentLimits> getUserPaymentLimits(Long userId) {
        log.warn("User Service unavailable - using fallback for getUserPaymentLimits: {}", userId);
        return ResponseEntity.ok(createFallbackPaymentLimits());
    }

    @Override
    public ResponseEntity<Void> updateLastPaymentActivity(Long userId) {
        log.warn("User Service unavailable - cannot update last payment activity for user: {}", userId);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> incrementPaymentCount(Long userId) {
        log.warn("User Service unavailable - cannot increment payment count for user: {}", userId);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<UserDto.RiskAssessment> getUserRiskAssessment(Long userId) {
        log.warn("User Service unavailable - using fallback for getUserRiskAssessment: {}", userId);
        return ResponseEntity.ok(createFallbackRiskAssessment());
    }

    /**
     * Creates a fallback UserDto with minimal information
     */
    private UserDto createFallbackUserDto(Long userId) {
        return UserDto.builder()
                .id(userId)
                .username("fallback-user")
                .email("fallback@example.com")
                .firstName("Fallback")
                .lastName("User")
                .status("UNKNOWN")
                .role("CUSTOMER")
                .canMakePayments(false)
                .paymentStatus("UNKNOWN")
                .paymentCount(0)
                .totalPaymentAmount(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * Creates fallback payment limits (restrictive)
     */
    private UserDto.PaymentLimits createFallbackPaymentLimits() {
        return UserDto.PaymentLimits.builder()
                .hasLimits(true)
                .dailyLimit(BigDecimal.ZERO)
                .monthlyLimit(BigDecimal.ZERO)
                .transactionLimit(BigDecimal.ZERO)
                .remainingDailyLimit(BigDecimal.ZERO)
                .remainingMonthlyLimit(BigDecimal.ZERO)
                .maxTransactionsPerDay(0)
                .remainingTransactionsToday(0)
                .build();
    }

    /**
     * Creates fallback risk assessment (high risk)
     */
    private UserDto.RiskAssessment createFallbackRiskAssessment() {
        return UserDto.RiskAssessment.builder()
                .riskLevel("HIGH")
                .riskScore(90)
                .riskReason("Service unavailable - defaulting to high risk")
                .requiresAdditionalVerification(true)
                .allowPayments(false)
                .lastAssessmentAt(LocalDateTime.now())
                .isNewUser(false)
                .hasRecentFailedPayments(false)
                .hasUnusualActivity(true)
                .isFromHighRiskLocation(false)
                .recentPaymentCount(0)
                .recentPaymentAmount(BigDecimal.ZERO)
                .build();
    }
}
