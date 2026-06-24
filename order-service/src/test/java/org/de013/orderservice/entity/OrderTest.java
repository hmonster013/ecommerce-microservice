package org.de013.orderservice.entity;

import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.entity.valueobject.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    @Test
    void recalculateTotals_WithMultipleItems_ShouldCalculateCorrectly() {
        Order order = Order.builder()
                .orderNumber("ORD-1001")
                .userId("user-123")
                .orderItems(new ArrayList<>())
                .shippingAmount(Money.of(15.00, "USD"))
                .build();

        OrderItem item1 = OrderItem.builder()
                .productId(1L)
                .sku("SKU-1")
                .productName("Item 1")
                .quantity(1)
                .unitPrice(Money.of(100.00, "USD"))
                .totalPrice(Money.of(100.00, "USD"))
                .discountAmount(Money.of(10.00, "USD"))
                .taxAmount(Money.of(8.00, "USD"))
                .build();

        OrderItem item2 = OrderItem.builder()
                .productId(2L)
                .sku("SKU-2")
                .productName("Item 2")
                .quantity(2)
                .unitPrice(Money.of(50.00, "USD"))
                .totalPrice(Money.of(100.00, "USD"))
                .discountAmount(Money.of(5.00, "USD"))
                .taxAmount(Money.of(8.00, "USD"))
                .build();

        order.addOrderItem(item1);
        order.addOrderItem(item2);

        order.recalculateTotals();

        // subtotal = 100 + 100 = 200
        assertEquals(BigDecimal.valueOf(200.00), order.getSubtotalAmount().getAmount());
        // discount = 10 + 5 = 15
        assertEquals(BigDecimal.valueOf(15.00), order.getDiscountAmount().getAmount());
        // tax = 8 + 8 = 16
        assertEquals(BigDecimal.valueOf(16.00), order.getTaxAmount().getAmount());
        // shipping = 15
        assertEquals(BigDecimal.valueOf(15.00), order.getShippingAmount().getAmount());
        // total = subtotal (200) - discount (15) + tax (16) + shipping (15) = 216
        assertEquals(BigDecimal.valueOf(216.00), order.getTotalAmount().getAmount());
    }

    @Test
    void updateStatus_ValidTransition_ShouldSucceed() {
        Order order = Order.builder()
                .status(OrderStatus.PENDING)
                .build();

        order.updateStatus(OrderStatus.PAID);
        assertEquals(OrderStatus.PAID, order.getStatus());
    }

    @Test
    void updateStatus_InvalidTransition_ShouldThrowException() {
        Order order = Order.builder()
                .status(OrderStatus.DELIVERED)
                .build();

        assertThrows(IllegalArgumentException.class, () -> order.updateStatus(OrderStatus.PENDING));
    }
}
