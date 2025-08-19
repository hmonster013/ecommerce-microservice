package org.de013.orderservice.service;

import org.de013.orderservice.dto.request.CreateOrderRequest;
import org.de013.orderservice.dto.response.OrderResponse;

public interface OrderProcessingService {
    OrderResponse placeOrder(CreateOrderRequest request);
    void startProcessing(Long orderId);
    void completeProcessing(Long orderId);
}

