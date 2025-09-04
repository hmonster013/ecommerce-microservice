package org.de013.paymentservice.service.external;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.paymentservice.client.UserServiceClient;
import org.de013.paymentservice.dto.external.UserDto;
import org.de013.paymentservice.dto.external.UserValidationResponse;
import org.de013.paymentservice.exception.ExternalServiceException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service for validating users and managing user-related operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserValidationService {

    private final UserServiceClient userServiceClient;

    /**
     * Validate user for payment processing
     */
    public UserValidationResponse validateUserForPayment(Long userId) {
        try {
            log.debug("Validating user for payment: {}", userId);
            ResponseEntity<UserValidationResponse> response = userServiceClient.validateUserForPayment(userId);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                UserValidationResponse validation = response.getBody();
                log.debug("User validation result: userId={}, valid={}, canPay={}, message={}", 
                         userId, validation.isValid(), validation.isPaymentAllowed(), validation.getMessage());
                return validation;
            } else {
                log.warn("Invalid response from user service for user validation: {}", userId);
                return UserValidationResponse.invalid(
                    "Invalid response from user service",
                    List.of("Service returned invalid response")
                );
            }
        } catch (Exception e) {
            log.error("Error validating user for payment: userId={}", userId, e);
            return UserValidationResponse.invalid(
                "Error validating user: " + e.getMessage(),
                List.of("Service communication error", e.getMessage())
            );
        }
    }

    /**
     * Get user details by ID
     */
    public UserDto getUserById(Long userId) {
        try {
            log.debug("Getting user by ID: {}", userId);
            ResponseEntity<UserDto> response = userServiceClient.getUserById(userId);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                throw new ExternalServiceException("User not found or service unavailable: " + userId);
            }
        } catch (Exception e) {
            log.error("Error getting user by ID: {}", userId, e);
            throw new ExternalServiceException("Failed to get user: " + e.getMessage(), e);
        }
    }

    /**
     * Check if user exists and is active
     */
    public boolean isUserActiveAndExists(Long userId) {
        try {
            log.debug("Checking if user is active and exists: {}", userId);
            
            // First check if user exists
            ResponseEntity<Boolean> existsResponse = userServiceClient.userExists(userId);
            if (!existsResponse.getStatusCode().is2xxSuccessful() || !Boolean.TRUE.equals(existsResponse.getBody())) {
                log.debug("User does not exist: {}", userId);
                return false;
            }
            
            // Then check if user is active
            ResponseEntity<Boolean> activeResponse = userServiceClient.isUserActive(userId);
            if (!activeResponse.getStatusCode().is2xxSuccessful() || !Boolean.TRUE.equals(activeResponse.getBody())) {
                log.debug("User is not active: {}", userId);
                return false;
            }
            
            log.debug("User is active and exists: {}", userId);
            return true;
        } catch (Exception e) {
            log.error("Error checking user status: userId={}", userId, e);
            return false;
        }
    }

    /**
     * Check if user can make payments
     */
    public boolean canUserMakePayments(Long userId) {
        try {
            log.debug("Checking if user can make payments: {}", userId);
            ResponseEntity<Boolean> response = userServiceClient.canUserMakePayments(userId);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                boolean canPay = response.getBody();
                log.debug("User payment capability: userId={}, canPay={}", userId, canPay);
                return canPay;
            } else {
                log.warn("Invalid response from user service for payment capability check: {}", userId);
                return false;
            }
        } catch (Exception e) {
            log.error("Error checking user payment capability: userId={}", userId, e);
            return false;
        }
    }

    /**
     * Get user payment limits
     */
    public UserDto.PaymentLimits getUserPaymentLimits(Long userId) {
        try {
            log.debug("Getting user payment limits: {}", userId);
            ResponseEntity<UserDto.PaymentLimits> response = userServiceClient.getUserPaymentLimits(userId);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                log.warn("Unable to get user payment limits: {}", userId);
                // Return restrictive limits as fallback
                return UserDto.PaymentLimits.builder()
                        .hasLimits(true)
                        .dailyLimit(BigDecimal.valueOf(1000))
                        .monthlyLimit(BigDecimal.valueOf(10000))
                        .transactionLimit(BigDecimal.valueOf(500))
                        .remainingDailyLimit(BigDecimal.ZERO)
                        .remainingMonthlyLimit(BigDecimal.ZERO)
                        .maxTransactionsPerDay(10)
                        .remainingTransactionsToday(0)
                        .build();
            }
        } catch (Exception e) {
            log.error("Error getting user payment limits: userId={}", userId, e);
            throw new ExternalServiceException("Failed to get user payment limits: " + e.getMessage(), e);
        }
    }

    /**
     * Get user risk assessment
     */
    public UserDto.RiskAssessment getUserRiskAssessment(Long userId) {
        try {
            log.debug("Getting user risk assessment: {}", userId);
            ResponseEntity<UserDto.RiskAssessment> response = userServiceClient.getUserRiskAssessment(userId);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                UserDto.RiskAssessment assessment = response.getBody();
                log.debug("User risk assessment: userId={}, riskLevel={}, riskScore={}, allowPayments={}", 
                         userId, assessment.getRiskLevel(), assessment.getRiskScore(), assessment.getAllowPayments());
                return assessment;
            } else {
                log.warn("Unable to get user risk assessment: {}", userId);
                // Return high risk as fallback
                return UserDto.RiskAssessment.builder()
                        .riskLevel("HIGH")
                        .riskScore(80)
                        .riskReason("Unable to assess risk - defaulting to high")
                        .requiresAdditionalVerification(true)
                        .allowPayments(false)
                        .build();
            }
        } catch (Exception e) {
            log.error("Error getting user risk assessment: userId={}", userId, e);
            throw new ExternalServiceException("Failed to get user risk assessment: " + e.getMessage(), e);
        }
    }

    /**
     * Validate payment amount against user limits
     */
    public boolean validatePaymentAmount(Long userId, BigDecimal amount) {
        try {
            UserDto.PaymentLimits limits = getUserPaymentLimits(userId);
            
            if (!limits.getHasLimits()) {
                log.debug("User has no payment limits: userId={}", userId);
                return true;
            }
            
            boolean canProcess = limits.canProcessAmount(amount) && limits.canProcessTransaction();
            log.debug("Payment amount validation: userId={}, amount={}, canProcess={}", userId, amount, canProcess);
            
            return canProcess;
        } catch (Exception e) {
            log.error("Error validating payment amount: userId={}, amount={}", userId, amount, e);
            return false;
        }
    }

    /**
     * Update user's last payment activity
     */
    public void updateLastPaymentActivity(Long userId) {
        try {
            log.debug("Updating last payment activity: {}", userId);
            ResponseEntity<Void> response = userServiceClient.updateLastPaymentActivity(userId);
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Failed to update last payment activity: userId={}, status={}", 
                        userId, response.getStatusCode());
            } else {
                log.debug("Successfully updated last payment activity: userId={}", userId);
            }
        } catch (Exception e) {
            log.error("Error updating last payment activity: userId={}", userId, e);
            // Don't throw exception as this is not critical
        }
    }

    /**
     * Increment user's payment count
     */
    public void incrementPaymentCount(Long userId) {
        try {
            log.debug("Incrementing payment count: {}", userId);
            ResponseEntity<Void> response = userServiceClient.incrementPaymentCount(userId);
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Failed to increment payment count: userId={}, status={}", 
                        userId, response.getStatusCode());
            } else {
                log.debug("Successfully incremented payment count: userId={}", userId);
            }
        } catch (Exception e) {
            log.error("Error incrementing payment count: userId={}", userId, e);
            // Don't throw exception as this is not critical
        }
    }

    /**
     * Comprehensive user validation for payment processing
     */
    public UserValidationResponse comprehensiveUserValidation(Long userId, BigDecimal paymentAmount) {
        try {
            log.debug("Performing comprehensive user validation: userId={}, amount={}", userId, paymentAmount);
            
            // Get basic validation
            UserValidationResponse basicValidation = validateUserForPayment(userId);
            if (!basicValidation.isValid()) {
                return basicValidation;
            }
            
            // Check payment amount against limits
            if (!validatePaymentAmount(userId, paymentAmount)) {
                return UserValidationResponse.invalid(
                    "Payment amount exceeds user limits",
                    List.of("Amount exceeds daily, monthly, or transaction limits")
                );
            }
            
            // Get risk assessment
            UserDto.RiskAssessment riskAssessment = getUserRiskAssessment(userId);
            if (riskAssessment.shouldBlockPayment()) {
                return UserValidationResponse.highRisk(
                    userId, 
                    basicValidation.getUsername(),
                    riskAssessment.getRiskLevel(),
                    riskAssessment.getRiskReason()
                );
            }
            
            log.debug("Comprehensive user validation passed: userId={}", userId);
            return basicValidation;
            
        } catch (Exception e) {
            log.error("Error in comprehensive user validation: userId={}, amount={}", userId, paymentAmount, e);
            return UserValidationResponse.invalid(
                "Error during user validation: " + e.getMessage(),
                List.of("Service error", e.getMessage())
            );
        }
    }
}
