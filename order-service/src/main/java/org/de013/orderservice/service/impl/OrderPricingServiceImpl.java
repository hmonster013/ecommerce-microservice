package org.de013.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.de013.orderservice.entity.Order;
import org.de013.orderservice.entity.OrderItem;
import org.de013.orderservice.entity.valueobject.Money;
import org.de013.orderservice.service.OrderPricingService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class OrderPricingServiceImpl implements OrderPricingService {

    @Override
    public void computeTotals(Order order) {
        if (order == null) return;
        String currency = order.getTotalAmount() != null ? order.getTotalAmount().getCurrency() :
                (order.getSubtotalAmount() != null ? order.getSubtotalAmount().getCurrency() : "USD");

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;

        if (order.getOrderItems() != null) {
            for (OrderItem item : order.getOrderItems()) {
                if (item.getUnitPrice() != null && item.getQuantity() != null) {
                    item.setTotalPrice(item.getUnitPrice().multiply(item.getQuantity()));
                }
                if (item.getTotalPrice() != null) subtotal = subtotal.add(item.getTotalPrice().getAmount());
                if (item.getDiscountAmount() != null) discount = discount.add(item.getDiscountAmount().getAmount());
                if (item.getTaxAmount() != null) tax = tax.add(item.getTaxAmount().getAmount());
            }
        }

        BigDecimal shipping = order.getShippingAmount() != null ? order.getShippingAmount().getAmount() : BigDecimal.ZERO;
        BigDecimal total = subtotal.add(tax).add(shipping).subtract(discount);

        order.setSubtotalAmount(Money.of(subtotal, currency));
        order.setDiscountAmount(Money.of(discount, currency));
        order.setTaxAmount(Money.of(tax, currency));
        order.setTotalAmount(Money.of(total, currency));
    }
}

