package org.de013.paymentservice.client;

import lombok.extern.slf4j.Slf4j;
import org.de013.paymentservice.dto.external.OrderDto;
import org.de013.paymentservice.dto.external.OrderStatusUpdateRequest;
import org.de013.paymentservice.dto.external.OrderValidationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Fallback implementation for OrderServiceClient
 * Reports unavailable or denies requests when Order Service is unavailable
 */
@Slf4j
@Component
public class OrderServiceClientFallback implements OrderServiceClient {

    @Override
    public ResponseEntity<OrderDto> getOrderById(Long orderId) {
        log.warn("Order Service unavailable - using fallback for getOrderById: {}", orderId);
        return ResponseEntity.status(503).build();
    }

    @Override
    public ResponseEntity<OrderDto> getOrderByNumber(String orderNumber) {
        log.warn("Order Service unavailable - using fallback for getOrderByNumber: {}", orderNumber);
        return ResponseEntity.status(503).build();
    }

    @Override
    public ResponseEntity<Void> updateOrderStatus(Long orderId, OrderStatusUpdateRequest request) {
        log.warn("Order Service unavailable - cannot update order status for order: {}", orderId);
        return ResponseEntity.status(503).build();
    }

    @Override
    public ResponseEntity<OrderValidationResponse> validateOrderForPayment(Long orderId) {
        log.warn("Order Service unavailable - using fallback validation for order: {}", orderId);
        return ResponseEntity.ok(OrderValidationResponse.invalid(
                "Order Service unavailable - cannot validate order",
                List.of("Service temporarily unavailable", "Please try again later")
        ));
    }

    @Override
    public ResponseEntity<BigDecimal> getOrderTotal(Long orderId) {
        log.warn("Order Service unavailable - using fallback for getOrderTotal: {}", orderId);
        return ResponseEntity.status(503).build();
    }

    @Override
    public ResponseEntity<Boolean> validateOrderOwnership(Long orderId, String userId) {
        log.warn("Order Service unavailable - using fallback for validateOrderOwnership: order={}, user={}",
                orderId, userId);
        return ResponseEntity.ok(false);
    }

    @Override
    public ResponseEntity<Void> markOrderAsPaid(Long orderId, Long paymentId, String paymentNumber) {
        log.warn("Order Service unavailable - cannot mark order as paid: order={}, payment={}",
                orderId, paymentId);
        return ResponseEntity.status(503).build();
    }

    @Override
    public ResponseEntity<Void> markOrderPaymentFailed(Long orderId, String reason) {
        log.warn("Order Service unavailable - cannot mark payment as failed: order={}, reason={}",
                orderId, reason);
        return ResponseEntity.status(503).build();
    }

    @Override
    public ResponseEntity<Boolean> reserveOrderItems(Long orderId) {
        log.warn("Order Service unavailable - cannot reserve items for order: {}", orderId);
        return ResponseEntity.ok(false);
    }

    @Override
    public ResponseEntity<Void> releaseOrderReservation(Long orderId) {
        log.warn("Order Service unavailable - cannot release reservation for order: {}", orderId);
        return ResponseEntity.status(503).build();
    }

}

