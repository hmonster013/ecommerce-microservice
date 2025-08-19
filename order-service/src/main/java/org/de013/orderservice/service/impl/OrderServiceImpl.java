package org.de013.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.de013.orderservice.dto.request.CreateOrderRequest;
import org.de013.orderservice.dto.request.UpdateOrderRequest;
import org.de013.orderservice.dto.request.CancelOrderRequest;
import org.de013.orderservice.dto.response.OrderResponse;
import org.de013.orderservice.entity.Order;
import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.exception.BadRequestException;
import org.de013.orderservice.exception.NotFoundException;
import org.de013.orderservice.mapper.OrderMapper;
import org.de013.orderservice.repository.OrderRepository;
import org.de013.orderservice.service.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderValidationService validationService;
    private final OrderPricingService pricingService;
    private final OrderStateMachine orderStateMachine;
    private final OrderMapper orderMapper;
    private final org.de013.orderservice.service.integration.OrderEventPublisher eventPublisher;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        validationService.validateCreate(request);
        // Generate order number
        String orderNumber = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        while (orderRepository.existsByOrderNumber(orderNumber)) {
            orderNumber = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        Order order = orderMapper.toEntity(request);
        order.setOrderNumber(orderNumber);
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        pricingService.computeTotals(order);
        order = orderRepository.save(order);

        // Publish OrderCreatedEvent
        eventPublisher.publishOrderCreated(org.de013.orderservice.dto.event.OrderEvents.OrderCreatedEvent.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .currency(order.getTotalAmount() != null ? order.getTotalAmount().getCurrency() : null)
                .createdAt(order.getCreatedAt())
                .build());

        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id).orElseThrow(() -> new NotFoundException("Order not found"));
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderByNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber).orElseThrow(() -> new NotFoundException("Order not found"));
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> listOrders(Pageable pageable) {
        return orderRepository.findAll(pageable).map(orderMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> listOrdersByUser(Long userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable).map(orderMapper::toResponse);
    }

    @Override
    @Transactional
    public OrderResponse updateOrder(Long id, UpdateOrderRequest request) {
        if (request == null || request.getOrderId() == null || !request.getOrderId().equals(id)) {
            throw new BadRequestException("orderId mismatch");
        }
        validationService.validateUpdate(request);
        Order order = orderRepository.findById(id).orElseThrow(() -> new NotFoundException("Order not found"));
        if (!order.canBeModified()) throw new BadRequestException("Order cannot be modified in current status");

        orderMapper.applyUpdate(order, request);
        pricingService.computeTotals(order);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long id, CancelOrderRequest request) {
        Order order = orderRepository.findById(id).orElseThrow(() -> new NotFoundException("Order not found"));
        if (!order.canBeCancelled()) throw new BadRequestException("Order cannot be cancelled");
        order.cancel(request.getReason());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        eventPublisher.publishOrderCancelled(org.de013.orderservice.dto.event.OrderEvents.OrderCancelledEvent.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .reason(request != null ? request.getReason() : null)
                .cancelledAt(order.getCancelledAt())
                .build());
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public void deleteOrder(Long id) {
        Order order = orderRepository.findById(id).orElseThrow(() -> new NotFoundException("Order not found"));
        order.softDelete();
        orderRepository.save(order);
    }
}

