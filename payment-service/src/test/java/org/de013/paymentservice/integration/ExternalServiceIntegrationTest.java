package org.de013.paymentservice.integration;

import org.de013.paymentservice.dto.external.OrderValidationResponse;
import org.de013.paymentservice.dto.external.UserValidationResponse;
import org.de013.paymentservice.service.external.OrderValidationService;
import org.de013.paymentservice.service.external.UserValidationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for external service clients
 */
@SpringBootTest
@ActiveProfiles("test")
class ExternalServiceIntegrationTest {

    @Autowired
    private OrderValidationService orderValidationService;

    @Autowired
    private UserValidationService userValidationService;

    @Test
    void testOrderValidationServiceFallback() {
        // Test fallback behavior when order service is unavailable
        Long orderId = 999999L;

        OrderValidationResponse response = orderValidationService.validateOrderForPayment(orderId);

        assertNotNull(response);
        assertFalse(response.isValid());
        assertNotNull(response.getMessage());
        assertTrue(response.getMessage().contains("unavailable"));
    }

    @Test
    void testUserValidationServiceFallback() {
        // Test fallback behavior when user service is unavailable
        String userId = "999999";

        UserValidationResponse response = userValidationService.validateUserForPayment(userId);

        assertNotNull(response);
        assertFalse(response.isValid());
        assertNotNull(response.getMessage());
        assertTrue(response.getMessage().contains("unavailable"));
    }

    @Test
    void testOrderOwnershipValidationFallback() {
        // Test fallback behavior for order ownership validation
        Long orderId = 999999L;
        String userId = "999999";

        boolean isOwner = orderValidationService.validateOrderOwnership(orderId, userId);

        assertFalse(isOwner); // Should return false when service is unavailable
    }

    @Test
    void testOrderServiceOperationsFallback() {
        // Test various order service operations fallback
        Long orderId = 999999L;
        String reason = "Test reason";

        // These should not throw exceptions
        assertDoesNotThrow(() -> {
            orderValidationService.markOrderPaymentFailed(orderId, reason);
            orderValidationService.releaseOrderReservation(orderId);
            orderValidationService.updateOrderStatus(orderId, "FAILED", reason);
        });

        // These should return false/null for fallback
        assertFalse(orderValidationService.reserveOrderItems(orderId));
    }
}
