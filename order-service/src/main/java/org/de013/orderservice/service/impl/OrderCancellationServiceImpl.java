package org.de013.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.de013.orderservice.dto.request.CancelOrderRequest;
import org.de013.orderservice.dto.response.OrderResponse;
import org.de013.orderservice.entity.Order;
import org.de013.orderservice.exception.BadRequestException;
import org.de013.orderservice.exception.NotFoundException;
import org.de013.orderservice.mapper.OrderMapper;
import org.de013.orderservice.repository.OrderRepository;
import org.de013.orderservice.service.OrderCancellationService;
import org.de013.orderservice.service.integration.InventoryIntegrationService;
import org.de013.orderservice.service.integration.NotificationIntegrationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OrderCancellationServiceImpl implements OrderCancellationService {

    private final OrderRepository orderRepository;
    private final InventoryIntegrationService inventoryIntegrationService;
    private final NotificationIntegrationService notificationIntegrationService;
    private final OrderMapper mapper;

    @Override
    @Transactional
    public OrderResponse cancel(Long orderId, CancelOrderRequest request) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new NotFoundException("Order not found"));
        if (!order.canBeCancelled()) throw new BadRequestException("Order cannot be cancelled in current status");

        order.cancel(request.getReason());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        // Release inventory if needed and notify
        if (Boolean.TRUE.equals(request.getRestockItems())) {
            inventoryIntegrationService.release(order);
        }
        if (Boolean.TRUE.equals(request.getSendNotification())) {
            notificationIntegrationService.sendOrderCancelled(order);
        }
        return mapper.toResponse(order);
    }
}

