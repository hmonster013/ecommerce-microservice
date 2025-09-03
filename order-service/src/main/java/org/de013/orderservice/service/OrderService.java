package org.de013.orderservice.service;

import org.de013.orderservice.dto.request.CreateOrderRequest;
import org.de013.orderservice.dto.request.UpdateOrderRequest;
import org.de013.orderservice.dto.response.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Basic Order Service - Core operations only
 */
public interface OrderService {
    // Create operations
    OrderResponse createOrder(CreateOrderRequest request);

    // Read operations
    OrderResponse getOrderById(Long id);
    OrderResponse getOrderByNumber(String orderNumber);
    Page<OrderResponse> listOrdersByUser(Long userId, Pageable pageable);
    Page<OrderResponse> listAllOrders(Pageable pageable);

    // Update operations
    OrderResponse updateOrder(Long id, UpdateOrderRequest request);

    // Delete operations
    void cancelOrder(Long id, String reason);
}

