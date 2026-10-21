package org.de013.paymentservice.dto.payment;

import java.math.BigDecimal;

public record HostedCheckoutResponse(Long orderId, String orderNumber, String state,
        BigDecimal amount, String currency, String redirectUrl, String paymentNumber) {}
