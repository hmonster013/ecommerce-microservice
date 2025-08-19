package org.de013.orderservice.service;

import org.de013.orderservice.dto.response.OrderResponse;
import org.de013.orderservice.entity.enums.OrderStatus;

public interface OrderTrackingService {
    OrderResponse advanceStatus(Long orderId, OrderStatus targetStatus, String note);
    OrderResponse markShipped(Long orderId, String trackingNumber, String carrier);
    OrderResponse markDelivered(Long orderId);
}

