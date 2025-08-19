package org.de013.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.de013.orderservice.dto.response.OrderResponse;
import org.de013.orderservice.entity.Order;
import org.de013.orderservice.entity.OrderTracking;
import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.entity.enums.TrackingStatus;
import org.de013.orderservice.exception.NotFoundException;
import org.de013.orderservice.mapper.OrderMapper;
import org.de013.orderservice.repository.OrderRepository;
import org.de013.orderservice.repository.OrderTrackingRepository;
import org.de013.orderservice.service.OrderStateMachine;
import org.de013.orderservice.service.OrderTrackingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static org.de013.orderservice.entity.enums.TrackingStatus.*;


import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OrderTrackingServiceImpl implements OrderTrackingService {

    private final OrderRepository orderRepository;
    private final OrderTrackingRepository trackingRepository;
    private final OrderStateMachine stateMachine;
    private final org.de013.orderservice.service.integration.OrderEventPublisher eventPublisher;

    private final OrderMapper mapper;

    @Override
    @Transactional
    public OrderResponse advanceStatus(Long orderId, OrderStatus targetStatus, String note) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new NotFoundException("Order not found"));
        stateMachine.assertTransitionAllowed(order.getStatus(), targetStatus);
        stateMachine.applyTransition(order, targetStatus);

        OrderTracking tr = OrderTracking.builder()
                .order(order)
                .trackingStatus(mapFromOrderStatus(targetStatus))
                .timestamp(LocalDateTime.now())
                .notes(note)
                .isAutomated(false)
                .updateSource("SYSTEM")
                .build();
        trackingRepository.save(tr);
        orderRepository.save(order);
        // Publish status changed
        publishStatusChanged(order, targetStatus, note);
        return mapper.toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse markShipped(Long orderId, String trackingNumber, String carrier) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new NotFoundException("Order not found"));
        stateMachine.assertTransitionAllowed(order.getStatus(), OrderStatus.SHIPPED);
        stateMachine.applyTransition(order, OrderStatus.SHIPPED);

        OrderTracking tr = OrderTracking.builder()
                .order(order)
                .trackingStatus(TrackingStatus.SHIPPED)
                .timestamp(LocalDateTime.now())
                .trackingNumber(trackingNumber)
                .carrier(carrier)
                .isAutomated(false)
                .updateSource("SYSTEM")
                .build();
        trackingRepository.save(tr);
        orderRepository.save(order);
        publishShipped(order, trackingNumber, carrier);
        return mapper.toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse markDelivered(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new NotFoundException("Order not found"));
        stateMachine.assertTransitionAllowed(order.getStatus(), OrderStatus.DELIVERED);
        stateMachine.applyTransition(order, OrderStatus.DELIVERED);

        OrderTracking tr = OrderTracking.builder()
                .order(order)
                .trackingStatus(TrackingStatus.DELIVERED)
                .timestamp(LocalDateTime.now())
                .isAutomated(false)
                .updateSource("SYSTEM")
                .build();
        trackingRepository.save(tr);
        orderRepository.save(order);
        publishDelivered(order);
        return mapper.toResponse(order);
    }
    private TrackingStatus mapFromOrderStatus(OrderStatus status) {
        return switch (status) {
            case PENDING, CONFIRMED -> ORDER_PLACED;
            case PAYMENT_AUTHORIZED, PAID, PROCESSING -> PROCESSING;
            case PREPARING -> READY_FOR_SHIPMENT;
            case SHIPPED -> SHIPPED;
            case OUT_FOR_DELIVERY -> OUT_FOR_DELIVERY;
            case DELIVERED -> DELIVERED;
            case COMPLETED -> DELIVERY_CONFIRMED;
            case CANCELLED -> CANCELLED;
            case REFUNDED -> RETURNED;
            case FAILED, ON_HOLD, PARTIALLY_SHIPPED, PARTIALLY_DELIVERED, RETURNING, RETURNED -> ON_HOLD;
        };
    }

    private void publishStatusChanged(Order order, OrderStatus to, String reason) {
        eventPublisher.publishOrderStatusChanged(org.de013.orderservice.dto.event.OrderEvents.OrderStatusChangedEvent.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .previousStatus(null) // previous can be added if we load it earlier
                .newStatus(to.name())
                .changedAt(java.time.LocalDateTime.now())
                .reason(reason)
                .build());
    }

    private void publishShipped(Order order, String trackingNumber, String carrier) {
        eventPublisher.publishOrderShipped(org.de013.orderservice.dto.event.OrderEvents.OrderShippedEvent.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .trackingNumber(trackingNumber)
                .carrier(carrier)
                .shippedAt(java.time.LocalDateTime.now())
                .build());
    }

    private void publishDelivered(Order order) {
        eventPublisher.publishOrderDelivered(org.de013.orderservice.dto.event.OrderEvents.OrderDeliveredEvent.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .deliveredAt(java.time.LocalDateTime.now())
                .build());
    }

}

