package org.de013.orderservice.service.integration;

import org.de013.orderservice.entity.Order;

public interface NotificationIntegrationService {
    void sendOrderPlaced(Order order);
    void sendOrderShipped(Order order);
    void sendOrderDelivered(Order order);
    void sendOrderCancelled(Order order);
}

