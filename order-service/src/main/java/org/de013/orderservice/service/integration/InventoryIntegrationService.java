package org.de013.orderservice.service.integration;

import org.de013.orderservice.entity.Order;

public interface InventoryIntegrationService {
    void reserve(Order order);
    void release(Order order);
}

