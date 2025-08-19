package org.de013.orderservice.service.integration.impl;

import lombok.RequiredArgsConstructor;
import org.de013.orderservice.entity.Order;
import org.de013.orderservice.service.integration.PaymentIntegrationService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PaymentIntegrationServiceImpl implements PaymentIntegrationService {

    @Override
    public void authorizePayment(Order order) {
        // Placeholder: call payment gateway to authorize
    }

    @Override
    public void capturePayment(Order order) {
        // Placeholder: capture payment
    }

    @Override
    public void refundPayment(Order order, BigDecimal amount, String reason) {
        // Placeholder: refund via payment gateway
    }
}

