package org.de013.paymentservice.client;

import org.de013.paymentservice.dto.external.UserDto;
import org.de013.paymentservice.dto.external.UserValidationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

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
     * Validate user for payment processing
     */
    @GetMapping("/{userId}/validate-payment")
    ResponseEntity<UserValidationResponse> validateUserForPayment(@PathVariable("userId") String userId);
}

