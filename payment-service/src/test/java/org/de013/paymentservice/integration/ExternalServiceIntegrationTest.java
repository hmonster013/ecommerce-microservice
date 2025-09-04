package org.de013.paymentservice.integration;

import org.de013.paymentservice.dto.external.OrderDto;
import org.de013.paymentservice.dto.external.OrderValidationResponse;
import org.de013.paymentservice.dto.external.UserDto;
import org.de013.paymentservice.dto.external.UserValidationResponse;
import org.de013.paymentservice.service.external.OrderValidationService;
import org.de013.paymentservice.service.external.UserValidationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

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
        Long userId = 999999L;
        
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
        Long userId = 999999L;
        
        boolean isOwner = orderValidationService.validateOrderOwnership(orderId, userId);
        
        assertFalse(isOwner); // Should return false when service is unavailable
    }

    @Test
    void testUserActiveCheckFallback() {
        // Test fallback behavior for user active check
        Long userId = 999999L;
        
        boolean isActive = userValidationService.isUserActiveAndExists(userId);
        
        assertFalse(isActive); // Should return false when service is unavailable
    }

    @Test
    void testPaymentAmountValidationFallback() {
        // Test fallback behavior for payment amount validation
        Long userId = 999999L;
        BigDecimal amount = new BigDecimal("100.00");
        
        boolean isValid = userValidationService.validatePaymentAmount(userId, amount);
        
        assertFalse(isValid); // Should return false when service is unavailable
    }

    @Test
    void testComprehensiveUserValidationFallback() {
        // Test comprehensive user validation fallback
        Long userId = 999999L;
        BigDecimal amount = new BigDecimal("100.00");
        
        UserValidationResponse response = userValidationService.comprehensiveUserValidation(userId, amount);
        
        assertNotNull(response);
        assertFalse(response.isValid());
        assertNotNull(response.getMessage());
    }

    @Test
    void testOrderTotalFallback() {
        // Test order total fallback
        Long orderId = 999999L;
        
        assertDoesNotThrow(() -> {
            BigDecimal total = orderValidationService.getOrderTotal(orderId);
            assertEquals(BigDecimal.ZERO, total);
        });
    }

    @Test
    void testUserPaymentLimitsFallback() {
        // Test user payment limits fallback
        Long userId = 999999L;
        
        assertDoesNotThrow(() -> {
            UserDto.PaymentLimits limits = userValidationService.getUserPaymentLimits(userId);
            assertNotNull(limits);
            assertTrue(limits.getHasLimits());
            assertEquals(BigDecimal.ZERO, limits.getRemainingDailyLimit());
        });
    }

    @Test
    void testUserRiskAssessmentFallback() {
        // Test user risk assessment fallback
        Long userId = 999999L;
        
        assertDoesNotThrow(() -> {
            UserDto.RiskAssessment assessment = userValidationService.getUserRiskAssessment(userId);
            assertNotNull(assessment);
            assertEquals("HIGH", assessment.getRiskLevel());
            assertFalse(assessment.getAllowPayments());
        });
    }

    @Test
    void testOrderServiceOperationsFallback() {
        // Test various order service operations fallback
        Long orderId = 999999L;
        Long paymentId = 123L;
        String paymentNumber = "PAY-123";
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

    @Test
    void testUserServiceOperationsFallback() {
        // Test various user service operations fallback
        Long userId = 999999L;
        
        // These should not throw exceptions
        assertDoesNotThrow(() -> {
            userValidationService.updateLastPaymentActivity(userId);
            userValidationService.incrementPaymentCount(userId);
        });
        
        // These should return false for fallback
        assertFalse(userValidationService.canUserMakePayments(userId));
    }
}
