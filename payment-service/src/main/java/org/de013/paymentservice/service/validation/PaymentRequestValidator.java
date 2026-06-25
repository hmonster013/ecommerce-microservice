package org.de013.paymentservice.service.validation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.exception.ConflictException;
import org.de013.paymentservice.client.OrderServiceClient;
import org.de013.paymentservice.client.UserServiceClient;
import org.de013.paymentservice.dto.external.OrderDto;
import org.de013.paymentservice.dto.external.UserValidationResponse;
import org.de013.paymentservice.dto.payment.ProcessPaymentRequest;
import org.de013.paymentservice.exception.PaymentProcessingException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Shared validation for payment requests, used by both the Stripe flow
 * ({@code PaymentServiceImpl}) and the VNPay flow ({@code VnpayService}).
 *
 * Extracted into a standalone component to avoid a circular dependency
 * (PaymentServiceImpl already depends on VnpayService) and to keep the
 * amount-tampering / fail-closed user checks defined in a single place.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRequestValidator {

    private final OrderServiceClient orderServiceClient;
    private final UserServiceClient userServiceClient;

    /**
     * Validates the base payment request: required fields, order amount/status
     * (Stripe amount-tampering protection) and user eligibility (fail-closed).
     */
    public void validatePaymentRequest(ProcessPaymentRequest request) {
        if (request == null) {
            throw new PaymentProcessingException("Payment request cannot be null");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentProcessingException("Payment amount must be greater than zero");
        }
        if (request.getOrderId() == null) {
            throw new PaymentProcessingException("Order ID is required");
        }
        if (request.getUserId() == null) {
            throw new PaymentProcessingException("User ID is required");
        }

        // Validate with Order Service (amount-tampering protection + non-payable state)
        validatePaymentAmount(request.getOrderId(), request.getAmount());

        // Validate with User Service (fail-closed: blocked / high-risk user)
        validateUserCanMakePayment(request.getUserId());
    }

    public void validatePaymentAmount(Long orderId, BigDecimal amount) {
        log.info("Validating payment amount {} for order {}", amount, orderId);
        try {
            ResponseEntity<OrderDto> response = orderServiceClient.getOrderById(orderId);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                OrderDto order = response.getBody();

                // Business rule validation: Do not allow payment for an order in a non-payable state
                if ("PAID".equals(order.getStatus()) || "COMPLETED".equals(order.getStatus())) {
                    log.error("Order {} is already paid or completed. Status: {}", orderId, order.getStatus());
                    throw new ConflictException("Order is already paid: " + orderId);
                }
                if ("CANCELLED".equals(order.getStatus())) {
                    log.error("Order {} is cancelled, cannot process payment", orderId);
                    throw new ConflictException("Order is cancelled: " + orderId);
                }

                BigDecimal orderTotal = order.getTotalAmount() != null ? order.getTotalAmount().getAmount() : BigDecimal.ZERO;
                if (orderTotal.compareTo(amount) != 0) {
                    log.error("Payment amount mismatch: request amount={}, order total={}", amount, orderTotal);
                    throw new PaymentProcessingException("Payment amount mismatch. Required: " + orderTotal + ", Provided: " + amount);
                }
                log.info("Payment amount matches order total: {}", orderTotal);
            } else {
                throw new PaymentProcessingException("Failed to validate payment: Order not found: " + orderId);
            }
        } catch (ConflictException | PaymentProcessingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error communicating with Order Service for verification: ", e);
            throw new PaymentProcessingException("Failed to verify payment amount: " + e.getMessage(), e);
        }
    }

    public void validateUserCanMakePayment(String userId) {
        log.info("Validating with User Service if user can make payment: {}", userId);
        try {
            ResponseEntity<UserValidationResponse> response = userServiceClient.validateUserForPayment(userId);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                UserValidationResponse validation = response.getBody();
                if (!validation.isValid()) {
                    log.error("User validation failed: {}", validation.getMessage());
                    throw new PaymentProcessingException("User is not allowed to make payment: " + validation.getMessage());
                }
                if (!validation.isCanMakePayments()) {
                    log.error("User cannot make payments. Reason: {}", validation.getPaymentBlockReason());
                    throw new PaymentProcessingException("User payment authorization is disabled: " + validation.getPaymentBlockReason());
                }
                log.info("User validation successful for user: {}", userId);
            } else {
                throw new PaymentProcessingException("Failed to validate user: User not found: " + userId);
            }
        } catch (PaymentProcessingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error communicating with User Service for verification: ", e);
            throw new PaymentProcessingException("User validation failed due to communication error: " + e.getMessage(), e);
        }
    }
}
