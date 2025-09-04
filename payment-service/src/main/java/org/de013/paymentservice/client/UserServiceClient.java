package org.de013.paymentservice.client;

import org.de013.paymentservice.dto.external.UserDto;
import org.de013.paymentservice.dto.external.UserValidationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Feign client for User Service integration
 */
@FeignClient(
    name = "user-service",
    path = "/api/v1/users",
    fallback = UserServiceClientFallback.class
)
public interface UserServiceClient {

    /**
     * Get user details by ID
     */
    @GetMapping("/{userId}")
    ResponseEntity<UserDto> getUserById(@PathVariable("userId") Long userId);

    /**
     * Get user details by email
     */
    @GetMapping("/email/{email}")
    ResponseEntity<UserDto> getUserByEmail(@PathVariable("email") String email);

    /**
     * Validate user for payment processing
     */
    @GetMapping("/{userId}/validate-payment")
    ResponseEntity<UserValidationResponse> validateUserForPayment(@PathVariable("userId") Long userId);

    /**
     * Check if user exists and is active
     */
    @GetMapping("/{userId}/exists")
    ResponseEntity<Boolean> userExists(@PathVariable("userId") Long userId);

    /**
     * Check if user is active
     */
    @GetMapping("/{userId}/active")
    ResponseEntity<Boolean> isUserActive(@PathVariable("userId") Long userId);

    /**
     * Check if user can make payments
     */
    @GetMapping("/{userId}/can-pay")
    ResponseEntity<Boolean> canUserMakePayments(@PathVariable("userId") Long userId);

    /**
     * Get user's payment limits
     */
    @GetMapping("/{userId}/payment-limits")
    ResponseEntity<UserDto.PaymentLimits> getUserPaymentLimits(@PathVariable("userId") Long userId);

    /**
     * Update user's last payment activity
     */
    @PutMapping("/{userId}/last-payment-activity")
    ResponseEntity<Void> updateLastPaymentActivity(@PathVariable("userId") Long userId);

    /**
     * Increment user's payment count
     */
    @PutMapping("/{userId}/increment-payment-count")
    ResponseEntity<Void> incrementPaymentCount(@PathVariable("userId") Long userId);

    /**
     * Check user's payment history for fraud detection
     */
    @GetMapping("/{userId}/payment-risk-assessment")
    ResponseEntity<UserDto.RiskAssessment> getUserRiskAssessment(@PathVariable("userId") Long userId);
}
