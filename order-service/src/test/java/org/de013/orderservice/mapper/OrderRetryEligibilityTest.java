package org.de013.orderservice.mapper;

import org.de013.orderservice.entity.Order;
import org.de013.orderservice.entity.enums.OrderStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class OrderRetryEligibilityTest {
    @Test
    void eligibilityIsStrictAndDoesNotMutateOrder() {
        var now = LocalDateTime.of(2026, 1, 1, 12, 0);
        var order = new Order();
        order.setAllocationPhase("READY");
        order.setStatus(OrderStatus.PENDING);
        order.setCheckoutDeadline(now.plusSeconds(1));
        assertTrue(OrderMapper.paymentRetryEligible(order, now));
        assertEquals("READY", order.getAllocationPhase());
        assertEquals(now.plusSeconds(1), order.getCheckoutDeadline());
        order.setCheckoutDeadline(now);
        assertFalse(OrderMapper.paymentRetryEligible(order, now));
        order.setCheckoutDeadline(now.minusSeconds(1));
        assertFalse(OrderMapper.paymentRetryEligible(order, now));
        order.setCheckoutDeadline(null);
        assertFalse(OrderMapper.paymentRetryEligible(order, now));
        order.setCheckoutDeadline(now.plusSeconds(1));
        for (String phase : new String[]{null, "ALLOCATING", "FULFILL_PENDING", "FULFILLED", "CLOSING", "CLOSURE_HOLD", "CLOSED", "FAILED"}) {
            order.setAllocationPhase(phase);
            assertFalse(OrderMapper.paymentRetryEligible(order, now));
        }
        order.setAllocationPhase("READY");
        for (OrderStatus status : OrderStatus.values()) {
            order.setStatus(status);
            assertEquals(status == OrderStatus.PENDING, OrderMapper.paymentRetryEligible(order, now));
        }
    }

    @Test
    void responseCarriesEligibilityWithoutChangingDeadlineOrAllocation() {
        var order = new Order();
        order.setAllocationPhase("READY");
        order.setStatus(OrderStatus.PENDING);
        var deadline = LocalDateTime.now().plusMinutes(30);
        order.setCheckoutDeadline(deadline);
        var response = new OrderMapper().toResponse(order);
        assertTrue(response.inventoryReady());
        assertTrue(response.paymentRetryEligible());
        assertEquals("READY", order.getAllocationPhase());
        assertEquals(deadline, order.getCheckoutDeadline());
    }
}
