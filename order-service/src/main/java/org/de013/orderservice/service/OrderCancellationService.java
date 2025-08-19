package org.de013.orderservice.service;

import org.de013.orderservice.dto.request.CancelOrderRequest;
import org.de013.orderservice.dto.response.OrderResponse;

public interface OrderCancellationService {
    OrderResponse cancel(Long orderId, CancelOrderRequest request);
}

