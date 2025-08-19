package org.de013.orderservice.service;

import org.de013.orderservice.dto.request.CreateOrderRequest;
import org.de013.orderservice.dto.request.UpdateOrderRequest;
import org.de013.orderservice.dto.request.CancelOrderRequest;
import org.de013.orderservice.dto.response.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {
    OrderResponse createOrder(CreateOrderRequest request);
    OrderResponse getOrderById(Long id);
    OrderResponse getOrderByNumber(String orderNumber);
    Page<OrderResponse> listOrders(Pageable pageable);
    OrderResponse updateOrder(Long id, UpdateOrderRequest request);
    OrderResponse cancelOrder(Long id, CancelOrderRequest request);
    void deleteOrder(Long id);
}

