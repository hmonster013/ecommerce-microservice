package org.de013.orderservice.service;

import org.de013.orderservice.entity.Order;

public interface OrderPricingService {
    void computeTotals(Order order);
}

