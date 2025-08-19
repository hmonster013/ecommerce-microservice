package org.de013.orderservice.service.integration;

import org.de013.orderservice.entity.Order;

import java.math.BigDecimal;

public interface PaymentIntegrationService {
    void authorizePayment(Order order);
    void capturePayment(Order order);
    void refundPayment(Order order, BigDecimal amount, String reason);
}

