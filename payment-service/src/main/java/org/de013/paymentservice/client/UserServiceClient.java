package org.de013.paymentservice.client;

import org.de013.paymentservice.dto.external.UserDto;
import org.de013.paymentservice.dto.external.UserValidationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

/**
 * Feign client for User Service integration
 */
@FeignClient(
        name = "user-service",
        path = "/users/internal",
        fallback = UserServiceClientFallback.class
)
public interface UserServiceClient {

    /**
     * Get user details by ID
     */
    @GetMapping("/by-keycloak-id/{userId}")
    ResponseEntity<org.de013.common.dto.ApiResponse<UserDto>> getUserById(@PathVariable("userId") String userId);

    /**
     * Get user details by email
     */
    @GetMapping("/email/{email}")
    ResponseEntity<UserDto> getUserByEmail(@PathVariable("email") String email);

    /**
     * Validate user for payment processing
     */
    @GetMapping("/{userId}/validate-payment")
    ResponseEntity<UserValidationResponse> validateUserForPayment(@PathVariable("userId") String userId);

    /**
     * Check if user exists and is active
     */
    @GetMapping("/{userId}/exists")
    ResponseEntity<Boolean> userExists(@PathVariable("userId") String userId);

    /**
     * Check if user is active
     */
    @GetMapping("/{userId}/active")
    ResponseEntity<Boolean> isUserActive(@PathVariable("userId") String userId);

    /**
     * Check if user can make payments
     */
    @GetMapping("/{userId}/can-pay")
    ResponseEntity<Boolean> canUserMakePayments(@PathVariable("userId") String userId);

    /**
     * Get user's payment limits
     */
    @GetMapping("/{userId}/payment-limits")
    ResponseEntity<UserDto.PaymentLimits> getUserPaymentLimits(@PathVariable("userId") String userId);

    /**
     * Update user's last payment activity
     */
    @PutMapping("/{userId}/last-payment-activity")
    ResponseEntity<Void> updateLastPaymentActivity(@PathVariable("userId") String userId);

    /**
     * Increment user's payment count
     */
    @PutMapping("/{userId}/increment-payment-count")
    ResponseEntity<Void> incrementPaymentCount(@PathVariable("userId") String userId);

    /**
     * Check user's payment history for fraud detection
     */
    @GetMapping("/{userId}/payment-risk-assessment")
    ResponseEntity<UserDto.RiskAssessment> getUserRiskAssessment(@PathVariable("userId") String userId);
}

