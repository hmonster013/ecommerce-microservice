package org.de013.paymentservice.client;

import org.de013.paymentservice.dto.external.OrderDto;
import org.de013.paymentservice.dto.external.OrderStatusUpdateRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Feign client for Order Service integration
 */
@FeignClient(
    name = "order-service",
    path = "/orders"
)
public interface OrderServiceClient {

    /**
     * Get order details by ID
     */
    @GetMapping("/{orderId}")
    ResponseEntity<OrderDto> getOrderById(@PathVariable("orderId") Long orderId);

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
    @GetMapping("/{orderId}/validate")
    ResponseEntity<Boolean> validateOrderForPayment(@PathVariable("orderId") Long orderId);

    /**
     * Get order total amount
     */
    @GetMapping("/{orderId}/total")
    ResponseEntity<Double> getOrderTotal(@PathVariable("orderId") Long orderId);
}
