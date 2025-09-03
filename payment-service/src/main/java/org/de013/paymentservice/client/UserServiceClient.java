package org.de013.paymentservice.client;

import org.de013.paymentservice.dto.external.UserDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Feign client for User Service integration
 */
@FeignClient(
    name = "user-service",
    path = "/users"
)
public interface UserServiceClient {

    /**
     * Get user details by ID
     */
    @GetMapping("/{userId}")
    ResponseEntity<UserDto> getUserById(@PathVariable("userId") Long userId);

    /**
     * Validate user exists and is active
     */
    @GetMapping("/{userId}/validate")
    ResponseEntity<Boolean> validateUser(@PathVariable("userId") Long userId);

    /**
     * Get user email for notifications
     */
    @GetMapping("/{userId}/email")
    ResponseEntity<String> getUserEmail(@PathVariable("userId") Long userId);
}
