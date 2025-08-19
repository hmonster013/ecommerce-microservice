package org.de013.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.de013.orderservice.entity.Order;
import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.exception.ConflictException;
import org.de013.orderservice.service.OrderStateMachine;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderStateMachineImpl implements OrderStateMachine {

    @Override
    public void assertTransitionAllowed(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) throw new IllegalArgumentException("Status cannot be null");
        if (!from.canTransitionTo(to)) {
            throw new ConflictException("Cannot transition from " + from + " to " + to);
        }
    }

    @Override
    public Order applyTransition(Order order, OrderStatus to) {
        assertTransitionAllowed(order.getStatus(), to);
        order.updateStatus(to);
        return order;
    }
}

