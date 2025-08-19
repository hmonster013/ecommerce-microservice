package org.de013.orderservice.service.integration.impl;

import lombok.RequiredArgsConstructor;
import org.de013.orderservice.entity.Order;
import org.de013.orderservice.service.integration.NotificationIntegrationService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationIntegrationServiceImpl implements NotificationIntegrationService {

    @Override
    public void sendOrderPlaced(Order order) {
        // Placeholder: notify user order placed
    }

    @Override
    public void sendOrderShipped(Order order) {
        // Placeholder: notify user order shipped
    }

    @Override
    public void sendOrderDelivered(Order order) {
        // Placeholder: notify user order delivered
    }

    @Override
    public void sendOrderCancelled(Order order) {
        // Placeholder: notify user order cancelled
    }
}

