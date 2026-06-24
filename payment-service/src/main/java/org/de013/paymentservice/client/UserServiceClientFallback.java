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
    public ResponseEntity<org.de013.common.dto.ApiResponse<UserDto>> getUserById(String userId) {
        log.warn("User Service unavailable - using fallback for getUserById: {}", userId);
        return ResponseEntity.ok(org.de013.common.dto.ApiResponse.success(createFallbackUserDto(userId)));
    }

    @Override
    public ResponseEntity<UserValidationResponse> validateUserForPayment(String userId) {
        log.warn("User Service unavailable - using fallback validation for user: {}", userId);
        return ResponseEntity.ok(UserValidationResponse.invalid(
                "User Service unavailable - cannot validate user",
                List.of("Service temporarily unavailable", "Please try again later")
        ));
    }

    /**
     * Creates a fallback UserDto with minimal information
     */
    private UserDto createFallbackUserDto(String userId) {
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
}
