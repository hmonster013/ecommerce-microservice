package org.de013.paymentservice.service.external;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.paymentservice.client.UserServiceClient;
import org.de013.paymentservice.dto.external.UserDto;
import org.de013.paymentservice.dto.external.UserValidationResponse;
import org.de013.paymentservice.exception.ExternalServiceException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

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
    public UserValidationResponse validateUserForPayment(String userId) {
        try {
            log.debug("Validating user for payment: {}", userId);
            ResponseEntity<UserValidationResponse> response = userServiceClient.validateUserForPayment(userId);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                UserValidationResponse validation = response.getBody();
                log.debug("User validation result: userId={}, valid={}, canPay={}, message={}",
                        userId, validation.isValid(), validation.isCanMakePayments(), validation.getMessage());
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
    public UserDto getUserById(String userId) {
        try {
            log.debug("Getting user by ID: {}", userId);
            ResponseEntity<org.de013.common.dto.ApiResponse<UserDto>> response = userServiceClient.getUserById(userId);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().getData() != null) {
                return response.getBody().getData();
            } else {
                throw new ExternalServiceException("User not found or service unavailable: " + userId);
            }
        } catch (Exception e) {
            log.error("Error getting user by ID: {}", userId, e);
            throw new ExternalServiceException("Failed to get user: " + e.getMessage(), e);
        }
    }
}
