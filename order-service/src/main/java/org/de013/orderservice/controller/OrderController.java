package org.de013.orderservice.controller;

import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderProcessingService processingService;
    private final OrderSearchRepository orderSearchRepository;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@RequestBody CreateOrderRequest request) {
        return processingService.placeOrder(request);
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@PathVariable Long orderId) {
        return orderService.getOrderById(orderId);
    }

    @PutMapping("/{orderId}")
    public OrderResponse update(@PathVariable Long orderId, @RequestBody UpdateOrderRequest request) {
        return orderService.updateOrder(orderId, request);
    }

    @DeleteMapping("/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long orderId, @RequestBody(required = false) CancelOrderRequest request) {
        CancelOrderRequest req = request != null ? request : new CancelOrderRequest();
        orderService.cancelOrder(orderId, req);
    }

    @GetMapping("/user/{userId}")
    public Page<OrderResponse> listByUser(@PathVariable Long userId, Pageable pageable) {
        return orderService.listOrdersByUser(userId, pageable);
    }

    @GetMapping("/search")
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

