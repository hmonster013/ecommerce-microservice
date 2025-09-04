package org.de013.paymentservice.service.external;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.paymentservice.client.OrderServiceClient;
import org.de013.paymentservice.dto.external.OrderDto;
import org.de013.paymentservice.dto.external.OrderStatusUpdateRequest;
import org.de013.paymentservice.dto.external.OrderValidationResponse;
import org.de013.paymentservice.exception.ExternalServiceException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service for validating orders and updating order status
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderValidationService {

    private final OrderServiceClient orderServiceClient;

    /**
     * Validate order for payment processing
     */
    public OrderValidationResponse validateOrderForPayment(Long orderId) {
        try {
            log.debug("Validating order for payment: {}", orderId);
            ResponseEntity<OrderValidationResponse> response = orderServiceClient.validateOrderForPayment(orderId);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                OrderValidationResponse validation = response.getBody();
                log.debug("Order validation result: orderId={}, valid={}, message={}", 
                         orderId, validation.isValid(), validation.getMessage());
                return validation;
            } else {
                log.warn("Invalid response from order service for order validation: {}", orderId);
                return OrderValidationResponse.invalid(
                    "Invalid response from order service",
                    List.of("Service returned invalid response")
                );
            }
        } catch (Exception e) {
            log.error("Error validating order for payment: orderId={}", orderId, e);
            return OrderValidationResponse.invalid(
                "Error validating order: " + e.getMessage(),
                List.of("Service communication error", e.getMessage())
            );
        }
    }

    /**
     * Get order details by ID
     */
    public OrderDto getOrderById(Long orderId) {
        try {
            log.debug("Getting order by ID: {}", orderId);
            ResponseEntity<OrderDto> response = orderServiceClient.getOrderById(orderId);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                throw new ExternalServiceException("Order not found or service unavailable: " + orderId);
            }
        } catch (Exception e) {
            log.error("Error getting order by ID: {}", orderId, e);
            throw new ExternalServiceException("Failed to get order: " + e.getMessage(), e);
        }
    }

    /**
     * Validate order ownership
     */
    public boolean validateOrderOwnership(Long orderId, Long userId) {
        try {
            log.debug("Validating order ownership: orderId={}, userId={}", orderId, userId);
            ResponseEntity<Boolean> response = orderServiceClient.validateOrderOwnership(orderId, userId);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                boolean isOwner = response.getBody();
                log.debug("Order ownership validation result: orderId={}, userId={}, isOwner={}", 
                         orderId, userId, isOwner);
                return isOwner;
            } else {
                log.warn("Invalid response from order service for ownership validation: orderId={}, userId={}", 
                        orderId, userId);
                return false;
            }
        } catch (Exception e) {
            log.error("Error validating order ownership: orderId={}, userId={}", orderId, userId, e);
            return false;
        }
    }

    /**
     * Get order total amount
     */
    public BigDecimal getOrderTotal(Long orderId) {
        try {
            log.debug("Getting order total: {}", orderId);
            ResponseEntity<BigDecimal> response = orderServiceClient.getOrderTotal(orderId);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            } else {
                throw new ExternalServiceException("Unable to get order total: " + orderId);
            }
        } catch (Exception e) {
            log.error("Error getting order total: {}", orderId, e);
            throw new ExternalServiceException("Failed to get order total: " + e.getMessage(), e);
        }
    }

    /**
     * Mark order as paid
     */
    public void markOrderAsPaid(Long orderId, Long paymentId, String paymentNumber) {
        try {
            log.info("Marking order as paid: orderId={}, paymentId={}, paymentNumber={}", 
                    orderId, paymentId, paymentNumber);
            
            ResponseEntity<Void> response = orderServiceClient.markOrderAsPaid(orderId, paymentId, paymentNumber);
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Failed to mark order as paid: orderId={}, status={}", 
                        orderId, response.getStatusCode());
                throw new ExternalServiceException("Failed to update order status to paid");
            }
            
            log.info("Successfully marked order as paid: orderId={}", orderId);
        } catch (Exception e) {
            log.error("Error marking order as paid: orderId={}, paymentId={}", orderId, paymentId, e);
            throw new ExternalServiceException("Failed to mark order as paid: " + e.getMessage(), e);
        }
    }

    /**
     * Mark order payment as failed
     */
    public void markOrderPaymentFailed(Long orderId, String reason) {
        try {
            log.info("Marking order payment as failed: orderId={}, reason={}", orderId, reason);
            
            ResponseEntity<Void> response = orderServiceClient.markOrderPaymentFailed(orderId, reason);
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Failed to mark order payment as failed: orderId={}, status={}", 
                        orderId, response.getStatusCode());
                // Don't throw exception here as this is not critical
            } else {
                log.info("Successfully marked order payment as failed: orderId={}", orderId);
            }
        } catch (Exception e) {
            log.error("Error marking order payment as failed: orderId={}", orderId, e);
            // Don't throw exception here as this is not critical for payment processing
        }
    }

    /**
     * Update order status
     */
    public void updateOrderStatus(Long orderId, String status, String reason) {
        try {
            log.info("Updating order status: orderId={}, status={}, reason={}", orderId, status, reason);
            
            OrderStatusUpdateRequest request = new OrderStatusUpdateRequest();
            request.setStatus(status);
            request.setReason(reason);
            request.setUpdatedBy("PAYMENT_SERVICE");
            
            ResponseEntity<Void> response = orderServiceClient.updateOrderStatus(orderId, request);
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Failed to update order status: orderId={}, status={}, responseStatus={}", 
                        orderId, status, response.getStatusCode());
                throw new ExternalServiceException("Failed to update order status");
            }
            
            log.info("Successfully updated order status: orderId={}, status={}", orderId, status);
        } catch (Exception e) {
            log.error("Error updating order status: orderId={}, status={}", orderId, status, e);
            throw new ExternalServiceException("Failed to update order status: " + e.getMessage(), e);
        }
    }

    /**
     * Reserve order items
     */
    public boolean reserveOrderItems(Long orderId) {
        try {
            log.debug("Reserving order items: {}", orderId);
            ResponseEntity<Boolean> response = orderServiceClient.reserveOrderItems(orderId);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                boolean reserved = response.getBody();
                log.debug("Order items reservation result: orderId={}, reserved={}", orderId, reserved);
                return reserved;
            } else {
                log.warn("Failed to reserve order items: orderId={}, status={}", 
                        orderId, response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("Error reserving order items: orderId={}", orderId, e);
            return false;
        }
    }

    /**
     * Release order items reservation
     */
    public void releaseOrderReservation(Long orderId) {
        try {
            log.debug("Releasing order reservation: {}", orderId);
            ResponseEntity<Void> response = orderServiceClient.releaseOrderReservation(orderId);
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Failed to release order reservation: orderId={}, status={}", 
                        orderId, response.getStatusCode());
            } else {
                log.debug("Successfully released order reservation: orderId={}", orderId);
            }
        } catch (Exception e) {
            log.error("Error releasing order reservation: orderId={}", orderId, e);
            // Don't throw exception as this is cleanup operation
        }
    }
}
