package org.de013.orderservice.service.integration;

import org.de013.orderservice.entity.Order;

public interface ShippingIntegrationService {
    void calculateShipping(Order order);
    void createShipment(Order order);
}

