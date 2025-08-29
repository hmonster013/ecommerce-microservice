package org.de013.orderservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.security.UserContext;
import org.de013.common.security.UserContextHolder;
import org.de013.orderservice.dto.request.CancelOrderRequest;
import org.de013.orderservice.dto.request.CreateOrderRequest;
import org.de013.orderservice.dto.request.UpdateOrderRequest;
import org.de013.orderservice.dto.response.OrderResponse;
import org.de013.orderservice.repository.custom.OrderSearchRepository;
import org.de013.orderservice.service.OrderProcessingService;
import org.de013.orderservice.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders") // Gateway routes /api/v1/orders/** to /orders/**
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;
    private final OrderProcessingService processingService;
    private final OrderSearchRepository orderSearchRepository;

    @PostMapping
    @PreAuthorize("@orderSecurity.isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@RequestBody CreateOrderRequest request) {
        // Get current user context from headers set by API Gateway
        UserContext userContext = UserContextHolder.getCurrentUser();
        if (userContext != null) {
            log.info("Creating order for user: {} (ID: {})", userContext.getUsername(), userContext.getUserId());
            // You can set userId in request if needed
            // request.setUserId(userContext.getUserId());
        }
        return processingService.placeOrder(request);
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("@orderSecurity.canAccessOrder(#orderId)")
    public OrderResponse get(@PathVariable Long orderId) {
        log.debug("Getting order {}", orderId);
        return orderService.getOrderById(orderId);
    }

    @PutMapping("/{orderId}")
    @PreAuthorize("@orderSecurity.canModifyOrder(#orderId)")
    public OrderResponse update(@PathVariable Long orderId, @RequestBody UpdateOrderRequest request) {
        log.debug("Updating order {}", orderId);
        return orderService.updateOrder(orderId, request);
    }

    @DeleteMapping("/{orderId}")
    @PreAuthorize("@orderSecurity.canModifyOrder(#orderId)")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long orderId, @RequestBody(required = false) CancelOrderRequest request) {
        log.debug("Cancelling order {}", orderId);
        CancelOrderRequest req = request != null ? request : new CancelOrderRequest();
        orderService.cancelOrder(orderId, req);
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("@orderSecurity.canAccessUserOrders(#userId)")
    public Page<OrderResponse> listByUser(@PathVariable Long userId, Pageable pageable) {
        log.debug("Getting orders for user {}", userId);
        return orderService.listOrdersByUser(userId, pageable);
    }

    @GetMapping("/my-orders")
    @PreAuthorize("@orderSecurity.isAuthenticated()")
    public Page<OrderResponse> getMyOrders(Pageable pageable) {
        UserContext userContext = UserContextHolder.requireAuthenticated();
        log.info("User {} requesting their orders", userContext.getUsername());
        return orderService.listOrdersByUser(userContext.getUserId(), pageable);
    }

    @GetMapping("/search")
    @PreAuthorize("@orderSecurity.isAdmin()")
    public Page<OrderResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) java.util.List<org.de013.orderservice.entity.enums.OrderStatus> statuses,
            @RequestParam(required = false) java.util.List<org.de013.orderservice.entity.enums.OrderType> orderTypes,
            @RequestParam(required = false) java.math.BigDecimal minAmount,
            @RequestParam(required = false) java.math.BigDecimal maxAmount,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime endDate,
            @RequestParam(required = false) String shippingCountry,
            @RequestParam(required = false) String shippingCity,
            @RequestParam(required = false) String customerEmail,
            @RequestParam(required = false) String customerPhone,
            @RequestParam(required = false) java.util.List<Long> productIds,
            @RequestParam(required = false) Boolean isGift,
            @RequestParam(required = false) Boolean requiresSpecialHandling,
            @RequestParam(required = false) java.util.List<Integer> priorityLevels,
            Pageable pageable
    ) {
        if ((q != null && !q.isBlank()) && userId == null && statuses == null && orderTypes == null && minAmount == null && maxAmount == null &&
                currency == null && startDate == null && endDate == null && shippingCountry == null && shippingCity == null &&
                customerEmail == null && customerPhone == null && productIds == null && isGift == null && requiresSpecialHandling == null && priorityLevels == null) {
            var page = orderSearchRepository.searchOrdersByText(q, pageable);
            return page.map(order -> orderService.getOrderById(order.getId()));
        }
        var page = orderSearchRepository.searchOrdersWithFilters(
                userId, statuses, orderTypes, minAmount, maxAmount, currency, startDate, endDate,
                shippingCountry, shippingCity, customerEmail, customerPhone, productIds, isGift, requiresSpecialHandling, priorityLevels, pageable
        );
        return page.map(order -> orderService.getOrderById(order.getId()));
    }
}

