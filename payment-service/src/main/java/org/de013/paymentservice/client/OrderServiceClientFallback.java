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
 * Provides default responses when Order Service is unavailable
 */
@Slf4j
@Component
public class OrderServiceClientFallback implements OrderServiceClient {

    @Override
    public ResponseEntity<OrderDto> getOrderById(Long orderId) {
        log.warn("Order Service unavailable - using fallback for getOrderById: {}", orderId);
        return ResponseEntity.ok(createFallbackOrderDto(orderId));
    }

    @Override
    public ResponseEntity<OrderDto> getOrderByNumber(String orderNumber) {
        log.warn("Order Service unavailable - using fallback for getOrderByNumber: {}", orderNumber);
        OrderDto fallbackOrder = createFallbackOrderDto(null);
        fallbackOrder.setOrderNumber(orderNumber);
        return ResponseEntity.ok(fallbackOrder);
    }

    @Override
    public ResponseEntity<Void> updateOrderStatus(Long orderId, OrderStatusUpdateRequest request) {
        log.warn("Order Service unavailable - cannot update order status for order: {}", orderId);
        return ResponseEntity.ok().build();
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
        return ResponseEntity.ok(BigDecimal.ZERO);
    }

    @Override
    public ResponseEntity<Boolean> validateOrderOwnership(Long orderId, Long userId) {
        log.warn("Order Service unavailable - using fallback for validateOrderOwnership: order={}, user={}", 
                orderId, userId);
        return ResponseEntity.ok(false);
    }

    @Override
    public ResponseEntity<Void> markOrderAsPaid(Long orderId, Long paymentId, String paymentNumber) {
        log.warn("Order Service unavailable - cannot mark order as paid: order={}, payment={}", 
                orderId, paymentId);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> markOrderPaymentFailed(Long orderId, String reason) {
        log.warn("Order Service unavailable - cannot mark payment as failed: order={}, reason={}", 
                orderId, reason);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Boolean> reserveOrderItems(Long orderId) {
        log.warn("Order Service unavailable - cannot reserve items for order: {}", orderId);
        return ResponseEntity.ok(false);
    }

    @Override
    public ResponseEntity<Void> releaseOrderReservation(Long orderId) {
        log.warn("Order Service unavailable - cannot release reservation for order: {}", orderId);
        return ResponseEntity.ok().build();
    }

    /**
     * Creates a fallback OrderDto with minimal information
     */
    private OrderDto createFallbackOrderDto(Long orderId) {
        OrderDto fallbackOrder = new OrderDto();
        fallbackOrder.setId(orderId);
        fallbackOrder.setOrderNumber("FALLBACK-ORDER");
        fallbackOrder.setUserId(null);
        fallbackOrder.setTotalAmount(BigDecimal.ZERO);
        fallbackOrder.setCurrency("USD");
        fallbackOrder.setStatus("UNKNOWN");
        return fallbackOrder;
    }
}
