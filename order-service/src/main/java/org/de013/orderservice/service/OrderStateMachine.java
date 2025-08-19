package org.de013.orderservice.service;

import org.de013.orderservice.entity.Order;
import org.de013.orderservice.entity.enums.OrderStatus;

public interface OrderStateMachine {
    void assertTransitionAllowed(OrderStatus from, OrderStatus to);
    Order applyTransition(Order order, OrderStatus to);
}

