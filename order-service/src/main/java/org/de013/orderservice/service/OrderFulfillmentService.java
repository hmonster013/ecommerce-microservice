package org.de013.orderservice.service;

import org.de013.orderservice.entity.Order;

public interface OrderFulfillmentService {
    void reserveStock(Order order);
    void releaseStock(Order order);
    void prepareShipment(Order order);
}

