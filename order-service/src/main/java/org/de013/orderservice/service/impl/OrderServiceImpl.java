package org.de013.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.orderservice.client.CartServiceClient;
import org.de013.orderservice.dto.request.CreateOrderRequest;
import org.de013.orderservice.dto.request.UpdateOrderRequest;
import org.de013.orderservice.dto.response.OrderResponse;
import org.de013.orderservice.entity.Order;
import org.de013.orderservice.entity.OrderItem;
import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.entity.valueobject.Money;
import org.de013.orderservice.exception.NotFoundException;
import org.de013.orderservice.mapper.OrderMapper;
import org.de013.orderservice.repository.OrderRepository;
import org.de013.orderservice.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Basic Order Service Implementation - Core operations only
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final CartServiceClient cartServiceClient;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating order for user: {} from cart: {}", request.getUserId(), request.getCartId());

        // Get cart items from Shopping Cart Service
        List<CartServiceClient.CartItemDto> cartItems = cartServiceClient.getCartItems(request.getCartId());
        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty or not found");
        }

        // Generate order number
        String orderNumber = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        while (orderRepository.existsByOrderNumber(orderNumber)) {
            orderNumber = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }

        // Create order from request
        Order order = orderMapper.toEntity(request);
        order.setOrderNumber(orderNumber);
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        // Convert cart items to order items
        for (CartServiceClient.CartItemDto cartItem : cartItems) {
            OrderItem orderItem = new OrderItem();

            // Convert String productId to Long (assuming it's numeric)
            try {
                orderItem.setProductId(Long.parseLong(cartItem.getProductId()));
            } catch (NumberFormatException e) {
                log.warn("Invalid productId format: {}, skipping item", cartItem.getProductId());
                continue;
            }

            orderItem.setSku(cartItem.getProductSku());
            orderItem.setProductName(cartItem.getProductName());
            orderItem.setProductDescription(cartItem.getProductDescription());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setUnitPrice(new Money(cartItem.getUnitPrice(), cartItem.getCurrency()));
            orderItem.setTotalPrice(new Money(cartItem.getTotalPrice(), cartItem.getCurrency()));
            orderItem.setOrder(order);
            order.getOrderItems().add(orderItem);
        }

        order = orderRepository.save(order);
        log.info("Order created successfully: {} with {} items", orderNumber, cartItems.size());

        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        log.debug("Getting order by ID: {}", id);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> listOrdersByUser(Long userId, Pageable pageable) {
        log.debug("Getting orders for user: {}", userId);
        return orderRepository.findByUserId(userId, pageable)
                .map(orderMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderByNumber(String orderNumber) {
        log.debug("Getting order by number: {}", orderNumber);
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> listAllOrders(Pageable pageable) {
        log.debug("Getting all orders");
        return orderRepository.findAll(pageable)
                .map(orderMapper::toResponse);
    }

    @Override
    @Transactional
    public OrderResponse updateOrder(Long id, UpdateOrderRequest request) {
        log.debug("Updating order: {}", id);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found"));

        // Basic update - can be enhanced later
        if (request.getCustomerNotes() != null) {
            order.setCustomerNotes(request.getCustomerNotes());
        }

        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public void cancelOrder(Long id, String reason) {
        log.debug("Cancelling order: {} with reason: {}", id, reason);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found"));

        // Basic cancellation - can be enhanced later
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancellationReason(reason);
        order.setUpdatedAt(LocalDateTime.now());

        orderRepository.save(order);
        log.info("Order {} cancelled successfully", id);
    }
}

