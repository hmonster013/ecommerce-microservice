package org.de013.paymentservice.client;

import org.de013.paymentservice.dto.external.OrderDto;
import org.de013.paymentservice.dto.external.OrderStatusUpdateRequest;
import org.de013.paymentservice.dto.external.OrderValidationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Feign client for Order Service integration
 */
@FeignClient(
    name = "order-service",
    path = "/api/v1/orders",
    fallback = OrderServiceClientFallback.class
)
public interface OrderServiceClient {

    /**
     * Get order details by ID
     */
    @GetMapping("/{orderId}")
    ResponseEntity<OrderDto> getOrderById(@PathVariable("orderId") Long orderId);

    /**
     * Get order details by order number
     */
    @GetMapping("/number/{orderNumber}")
    ResponseEntity<OrderDto> getOrderByNumber(@PathVariable("orderNumber") String orderNumber);

    /**
     * Update order status
     */
    @PutMapping("/{orderId}/status")
    ResponseEntity<Void> updateOrderStatus(
        @PathVariable("orderId") Long orderId,
        @RequestBody OrderStatusUpdateRequest request
    );

    /**
     * Validate order for payment processing
     */
    @GetMapping("/{orderId}/validate-payment")
    ResponseEntity<OrderValidationResponse> validateOrderForPayment(@PathVariable("orderId") Long orderId);

    /**
     * Get order total amount
     */
    @GetMapping("/{orderId}/total")
    ResponseEntity<BigDecimal> getOrderTotal(@PathVariable("orderId") Long orderId);

    /**
     * Check if order belongs to user
     */
    @GetMapping("/{orderId}/user/{userId}/validate")
    ResponseEntity<Boolean> validateOrderOwnership(
        @PathVariable("orderId") Long orderId,
        @PathVariable("userId") Long userId
    );

    /**
     * Mark order as paid
     */
    @PutMapping("/{orderId}/mark-paid")
    ResponseEntity<Void> markOrderAsPaid(
        @PathVariable("orderId") Long orderId,
        @RequestParam("paymentId") Long paymentId,
        @RequestParam("paymentNumber") String paymentNumber
    );

    /**
     * Mark order as payment failed
     */
    @PutMapping("/{orderId}/mark-payment-failed")
    ResponseEntity<Void> markOrderPaymentFailed(
        @PathVariable("orderId") Long orderId,
        @RequestParam("reason") String reason
    );

    /**
     * Reserve order items (inventory hold)
     */
    @PutMapping("/{orderId}/reserve")
    ResponseEntity<Boolean> reserveOrderItems(@PathVariable("orderId") Long orderId);

    /**
     * Release order items reservation
     */
    @PutMapping("/{orderId}/release-reservation")
    ResponseEntity<Void> releaseOrderReservation(@PathVariable("orderId") Long orderId);
}
