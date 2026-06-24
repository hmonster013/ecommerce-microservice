package org.de013.orderservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.orderservice.client.CartServiceClient;
import org.de013.orderservice.client.ProductCatalogClient;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
    private final ProductCatalogClient productCatalogClient;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating order for user: {} from cart: {}", request.getUserId(), request.getCartId());

        // Get cart items from Shopping Cart Service
        List<org.de013.orderservice.dto.CartItemDto> cartItems = cartServiceClient.getCartItems(request.getCartId()).getData();
        if (cartItems == null || cartItems.isEmpty()) {
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

        String currency = request.getCurrency() != null ? request.getCurrency() : "USD";
        order.setTaxAmount(new Money(BigDecimal.ZERO, currency));
        order.setShippingAmount(new Money(BigDecimal.ZERO, currency));
        order.setDiscountAmount(new Money(BigDecimal.ZERO, currency));

        // Convert cart items to order items and deduct stock
        for (org.de013.orderservice.dto.CartItemDto cartItem : cartItems) {
            OrderItem orderItem = new OrderItem();

            // Convert String productId to Long (assuming it's numeric)
            Long productId;
            try {
                productId = Long.parseLong(cartItem.getProductId());
                orderItem.setProductId(productId);
            } catch (NumberFormatException e) {
                log.warn("Invalid productId format: {}, skipping item", cartItem.getProductId());
                continue;
            }

            orderItem.setSku(cartItem.getProductSku());
            orderItem.setProductName(cartItem.getProductName());
            orderItem.setProductDescription(cartItem.getProductDescription());
            orderItem.setQuantity(cartItem.getQuantity());
            // unitPrice is the list price per unit; totalPrice is the GROSS line amount (unitPrice * qty).
            // The order-level recalculation subtracts the per-line discount once to reach the net total.
            BigDecimal qty = BigDecimal.valueOf(cartItem.getQuantity());
            orderItem.setUnitPrice(new Money(cartItem.getUnitPrice(), cartItem.getCurrency()));
            orderItem.setTotalPrice(new Money(cartItem.getUnitPrice().multiply(qty), cartItem.getCurrency()));

            // cart exposes discount per unit; scale by quantity for the line-level discount
            BigDecimal discountPerUnit = cartItem.getDiscountAmount() != null ? cartItem.getDiscountAmount() : BigDecimal.ZERO;
            orderItem.setDiscountAmount(new Money(discountPerUnit.multiply(qty), cartItem.getCurrency()));
            
            // Set tax amount (default to 0)
            orderItem.setTaxAmount(new Money(BigDecimal.ZERO, cartItem.getCurrency()));
            
            // Set product category and brand
            orderItem.setProductCategory(cartItem.getCategoryName());
            orderItem.setProductBrand(cartItem.getProductBrand());
            
            orderItem.setOrder(order);
            order.getOrderItems().add(orderItem);

            // Deduct stock in Product Catalog Service
            try {
                log.info("Deducting {} stock for product ID: {}", cartItem.getQuantity(), productId);
                productCatalogClient.removeStock(productId, cartItem.getQuantity());
            } catch (Exception e) {
                log.error("Failed to deduct stock for product ID: {} - Error: {}", productId, e.getMessage());
                throw new IllegalStateException("Failed to allocate inventory for product: " + cartItem.getProductName());
            }
        }

        order.recalculateTotals();

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
    public Page<OrderResponse> listOrdersByUser(String userId, Pageable pageable) {
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

    @Override
    @Transactional
    public void markOrderAsPaid(Long orderId, Long paymentId, String paymentNumber) {
        log.info("Marking order {} as PAID with paymentId {} and number {}", orderId, paymentId, paymentNumber);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        order.setStatus(OrderStatus.PAID);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        log.info("Order {} successfully marked as PAID", orderId);
    }

    @Override
    @Transactional
    public void markOrderPaymentFailed(Long orderId, String reason) {
        log.info("Marking order {} as FAILED due to: {}", orderId, reason);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        order.setStatus(OrderStatus.FAILED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        log.info("Order {} successfully marked as FAILED", orderId);
    }

    @Override
    @Transactional
    public void updateOrderStatus(Long orderId, org.de013.orderservice.dto.request.OrderStatusUpdateRequest request) {
        log.info("Updating order {} status to {}", orderId, request.getStatus());
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        OrderStatus newStatus = OrderStatus.fromCode(request.getStatus());
        if (newStatus == null) {
            throw new IllegalArgumentException("Invalid order status code: " + request.getStatus());
        }
        order.setStatus(newStatus);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        log.info("Order {} status successfully updated to {}", orderId, newStatus);
    }
}


