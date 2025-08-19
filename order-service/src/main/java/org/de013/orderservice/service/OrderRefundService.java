package org.de013.orderservice.service;

import org.de013.orderservice.dto.response.OrderResponse;

import java.math.BigDecimal;

public interface OrderRefundService {
    OrderResponse refund(Long orderId, BigDecimal amount, String reason);
}

