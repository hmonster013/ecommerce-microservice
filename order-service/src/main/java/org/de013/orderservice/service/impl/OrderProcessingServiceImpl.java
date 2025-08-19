package org.de013.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.de013.orderservice.dto.request.CreateOrderRequest;
import org.de013.orderservice.dto.response.OrderResponse;
import org.de013.orderservice.entity.Order;
import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.exception.BadRequestException;
import org.de013.orderservice.exception.NotFoundException;
import org.de013.orderservice.mapper.OrderMapper;
import org.de013.orderservice.redis.repository.OrderProcessingQueue;
import org.de013.orderservice.repository.OrderRepository;
import org.de013.orderservice.service.*;
import org.de013.orderservice.service.integration.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderProcessingServiceImpl implements OrderProcessingService {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final OrderValidationService validationService;
    private final OrderStateMachine stateMachine;
    private final OrderPricingService pricingService;
    private final CartIntegrationService cartIntegrationService;
    private final PaymentIntegrationService paymentIntegrationService;
    private final InventoryIntegrationService inventoryIntegrationService;
    private final ShippingIntegrationService shippingIntegrationService;
    private final NotificationIntegrationService notificationIntegrationService;
    private final OrderProcessingQueue processingQueue;

    @Override
    @Transactional
    public OrderResponse placeOrder(CreateOrderRequest request) {
        validationService.validateCreate(request);
        // Compute prices before calling payment/inventory/shipping
        OrderResponse created = orderService.createOrder(request);
        // Integration side-effects can be orchestrated asynchronously if needed
        processingQueue.enqueue("place", created.getOrderNumber());
        return created;
    }

    @Override
    @Transactional
    public void startProcessing(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        stateMachine.assertTransitionAllowed(order.getStatus(), OrderStatus.PROCESSING);
        stateMachine.applyTransition(order, OrderStatus.PROCESSING);
        order.setUpdatedAt(LocalDateTime.now());

        // Reserve inventory and calculate shipping
        inventoryIntegrationService.reserve(order);
        shippingIntegrationService.calculateShipping(order);

        orderRepository.save(order);
    }

    @Override
    @Transactional
    public void completeProcessing(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        stateMachine.assertTransitionAllowed(order.getStatus(), OrderStatus.PREPARING);
        stateMachine.applyTransition(order, OrderStatus.PREPARING);

        // Authorize and capture payment, then create shipment
        paymentIntegrationService.authorizePayment(order);
        paymentIntegrationService.capturePayment(order);
        shippingIntegrationService.createShipment(order);

        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        notificationIntegrationService.sendOrderPlaced(order);
    }
}

