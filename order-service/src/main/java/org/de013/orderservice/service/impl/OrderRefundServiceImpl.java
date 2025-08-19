package org.de013.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.de013.orderservice.dto.response.OrderResponse;
import org.de013.orderservice.entity.Order;
import org.de013.orderservice.entity.valueobject.Money;
import org.de013.orderservice.exception.BadRequestException;
import org.de013.orderservice.exception.NotFoundException;
import org.de013.orderservice.mapper.OrderMapper;
import org.de013.orderservice.repository.OrderRepository;
import org.de013.orderservice.service.OrderRefundService;
import org.de013.orderservice.service.integration.PaymentIntegrationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class OrderRefundServiceImpl implements OrderRefundService {

    private final OrderRepository orderRepository;
    private final PaymentIntegrationService paymentIntegrationService;
    private final OrderMapper mapper;

    @Override
    @Transactional
    public OrderResponse refund(Long orderId, BigDecimal amount, String reason) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new NotFoundException("Order not found"));
        if (order.getTotalAmount() == null || !order.getTotalAmount().isPositive()) {
            throw new BadRequestException("Order has no payable amount");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Invalid refund amount");
        }
        if (amount.compareTo(order.getTotalAmount().getAmount()) > 0) {
            throw new BadRequestException("Refund exceeds total amount");
        }
        paymentIntegrationService.refundPayment(order, amount, reason);
        orderRepository.save(order);
        return mapper.toResponse(order);
    }
}

