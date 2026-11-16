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

    private final jakarta.persistence.EntityManager entityManager;

    @Override
    public OrderResponse createOrder(CreateOrderRequest request) {
        throw new UnsupportedOperationException("Use idempotent checkout");
    }

    @Override
    public OrderResponse createCheckoutOrder(CreateOrderRequest request, String checkoutKey) {
        throw new UnsupportedOperationException("Use idempotent checkout");
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public OrderResponse createIntent(CreateOrderRequest request, String checkoutKey, String snapshot,
                                      List<org.de013.orderservice.dto.CartItemDto> cartItems) {
        int inserted = entityManager.createNativeQuery("INSERT INTO checkout_attempts (attempt_key,user_id,request_snapshot) VALUES (:key,:owner,:snapshot) ON CONFLICT (attempt_key) DO NOTHING")
                .setParameter("key", checkoutKey).setParameter("owner", request.userId())
                .setParameter("snapshot", snapshot).executeUpdate();
        if (inserted == 0) throw new org.springframework.dao.DuplicateKeyException("Attempt exists");
        // Generate order number
        String orderNumber = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        while (orderRepository.existsByOrderNumber(orderNumber)) {
            orderNumber = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }

        // Create order from request
        Order order = orderMapper.toEntity(request);
        order.setCheckoutKey(checkoutKey);
        order.setOrderNumber(orderNumber);
        order.setStatus(OrderStatus.ON_HOLD);
        order.setAllocationPhase("ALLOCATING");
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        String currency = request.currency() != null ? request.currency() : "USD";
        order.setTaxAmount(new Money(BigDecimal.ZERO, currency));
        order.setShippingAmount(new Money(BigDecimal.ZERO, currency));
        order.setDiscountAmount(new Money(BigDecimal.ZERO, currency));

        // Convert cart items to order items and deduct stock
        for (org.de013.orderservice.dto.CartItemDto cartItem : cartItems) {
            OrderItem orderItem = new OrderItem();

            orderItem.setProductId(cartItem.productId());

            orderItem.setSku(cartItem.productSku());
            orderItem.setProductName(cartItem.productName());
            orderItem.setProductDescription(cartItem.productDescription());
            orderItem.setQuantity(cartItem.quantity());
            // unitPrice is the list price per unit; totalPrice is the GROSS line amount (unitPrice * qty).
            // The order-level recalculation subtracts the per-line discount once to reach the net total.
            BigDecimal qty = BigDecimal.valueOf(cartItem.quantity());
            orderItem.setUnitPrice(new Money(cartItem.unitPrice(), cartItem.currency()));
            orderItem.setTotalPrice(new Money(cartItem.unitPrice().multiply(qty), cartItem.currency()));

            // cart exposes discount per unit; scale by quantity for the line-level discount
            BigDecimal discountPerUnit = cartItem.discountAmount() != null ? cartItem.discountAmount() : BigDecimal.ZERO;
            orderItem.setDiscountAmount(new Money(discountPerUnit.multiply(qty), cartItem.currency()));
            
            // Set tax amount (default to 0)
            orderItem.setTaxAmount(new Money(BigDecimal.ZERO, cartItem.currency()));
            
            // Set product category and brand
            orderItem.setProductCategory(cartItem.categoryName());
            orderItem.setProductBrand(cartItem.productBrand());
            
            orderItem.setOrder(order);
            order.getOrderItems().add(orderItem);


        }

        order.recalculateTotals();

        order = orderRepository.saveAndFlush(order);
        for (var item : cartItems) {
            entityManager.createNativeQuery("INSERT INTO inventory_allocation_intents (operation_id,order_id,target_kind,target_id,quantity,owner_id,state) VALUES (:id,:orderId,:kind,:target,:quantity,:owner,'PENDING')")
                    .setParameter("id", UUID.randomUUID()).setParameter("orderId", order.getId())
                    .setParameter("kind", item.variantId() == null ? "PRODUCT" : "VARIANT")
                    .setParameter("target", item.variantId() == null ? Long.parseLong(item.productId()) : Long.parseLong(item.variantId()))
                    .setParameter("quantity", item.quantity()).setParameter("owner", request.userId()).executeUpdate();
        }
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
        Order order = orderRepository.lockForPayment(id)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        if (order.getAllocationPhase() != null) throw new org.de013.common.exception.ConflictException("Order requires allocation reconciliation");

        // Basic update - can be enhanced later
        if (request.customerNotes() != null) {
            order.setCustomerNotes(request.customerNotes());
        }

        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);

        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public void cancelOrder(Long id, String reason) {
        log.debug("Cancelling order: {} with reason: {}", id, reason);
        Order order = orderRepository.lockForPayment(id)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        if (order.getAllocationPhase() != null) throw new org.de013.common.exception.ConflictException("Order requires allocation reconciliation");

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
        Order order = orderRepository.lockForPayment(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        if (order.getAllocationPhase() != null && java.util.Set.of("CLOSING", "RELEASE_PENDING", "CLOSED", "CLOSURE_HOLD").contains(order.getAllocationPhase())) {
            order.setAllocationPhase("CLOSURE_HOLD");
            order.setCheckoutCloseHoldReason("PAYMENT_SUCCESS_AFTER_CLOSURE");
            return;
        }
        if (order.getStatus() != null && order.getStatus().isPaidState()) {
            if (order.getAcceptedPaymentId() != null && (!order.getAcceptedPaymentId().equals(paymentId)
                    || !java.util.Objects.equals(order.getAcceptedPaymentNumber(), paymentNumber))) {
                throw new org.de013.common.exception.ConflictException("Order requires payment reconciliation");
            }
            return;
        }
        if (order.getAllocationPhase() != null && !"READY".equals(order.getAllocationPhase())) {
            throw new org.de013.common.exception.ConflictException("Order requires allocation reconciliation");
        }
        if (!java.util.EnumSet.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PAYMENT_AUTHORIZED).contains(order.getStatus())) {
            throw new org.de013.common.exception.ConflictException("Order requires payment reconciliation");
        }
        order.setStatus(OrderStatus.PAID);
        order.setAcceptedPaymentId(paymentId);
        order.setAcceptedPaymentNumber(paymentNumber);
        if (order.getAllocationPhase() != null) order.setAllocationPhase("FULFILL_PENDING");
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        log.info("Order {} successfully marked as PAID", orderId);
    }

    @Override
    @Transactional
    public void markOrderPaymentFailed(Long orderId, String reason) {
        log.info("Marking order {} as FAILED due to: {}", orderId, reason);
        Order order = orderRepository.lockForPayment(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        if (order.getAllocationPhase() != null) throw new org.de013.common.exception.ConflictException("Order requires allocation reconciliation");
        order.setStatus(OrderStatus.FAILED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        log.info("Order {} successfully marked as FAILED", orderId);
    }

    @Override
    @Transactional
    public void updateOrderStatus(Long orderId, org.de013.orderservice.dto.request.OrderStatusUpdateRequest request) {
        log.info("Updating order {} status to {}", orderId, request.status());
        Order order = orderRepository.lockForPayment(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        if (order.getAllocationPhase() != null) throw new org.de013.common.exception.ConflictException("Order requires allocation reconciliation");
        OrderStatus newStatus = OrderStatus.fromCode(request.status());
        if (newStatus == null) {
            throw new IllegalArgumentException("Invalid order status code: " + request.status());
        }
        order.setStatus(newStatus);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        log.info("Order {} status successfully updated to {}", orderId, newStatus);
    }

}


