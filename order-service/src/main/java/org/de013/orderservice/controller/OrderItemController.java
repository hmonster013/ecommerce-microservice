package org.de013.orderservice.controller;

import lombok.RequiredArgsConstructor;
import org.de013.orderservice.dto.response.OrderResponse;
import org.de013.orderservice.service.OrderService;
import org.de013.orderservice.service.OrderRefundService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderItemController {

    private final OrderService orderService;
    private final OrderRefundService refundService;

    @GetMapping("/{orderId}/items")
    public java.util.List<org.de013.orderservice.dto.response.OrderResponse.OrderItemResponse> getItems(@PathVariable Long orderId) {
        var order = orderService.getOrderById(orderId);
        return order.getOrderItems();
    }

    @PutMapping("/{orderId}/items/{itemId}")
    public OrderResponse updateItem(@PathVariable Long orderId, @PathVariable Long itemId, @RequestBody org.de013.orderservice.dto.request.UpdateOrderRequest.UpdateOrderItemDto req) {
        // Ensure path id consistency and default action
        req.setOrderItemId(itemId);
        if (req.getAction() == null || req.getAction().isBlank()) {
            req.setAction("UPDATE");
        }
        var wrapper = org.de013.orderservice.dto.request.UpdateOrderRequest.builder()
                .orderId(orderId)
                .orderItems(java.util.List.of(req))
                .build();
        return orderService.updateOrder(orderId, wrapper);
    }

    @PostMapping("/{orderId}/items/{itemId}/return")
    public OrderResponse initiateReturn(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @RequestParam(defaultValue = "CUSTOMER_REQUEST") String reason,
            @RequestParam(defaultValue = "1") Integer quantity,
            @RequestParam(required = false) java.math.BigDecimal amount,
            @RequestParam(defaultValue = "USD") String currency
    ) {
        // Nếu amount không truyền thì giả định refund theo đơn giá * quantity (đơn giản cho dev)
        var order = orderService.getOrderById(orderId);
        java.math.BigDecimal refundAmount = amount;
        if (refundAmount == null) {
            var item = order.getOrderItems().stream().filter(i -> i.getId().equals(itemId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Order item not found"));
            if (item.getFinalPrice() != null && item.getQuantity() != null && item.getQuantity() > 0) {
                var unit = item.getFinalPrice().getAmount().divide(java.math.BigDecimal.valueOf(item.getQuantity()), java.math.RoundingMode.HALF_UP);
                refundAmount = unit.multiply(java.math.BigDecimal.valueOf(quantity));
            } else if (item.getUnitPrice() != null) {
                refundAmount = item.getUnitPrice().getAmount().multiply(java.math.BigDecimal.valueOf(quantity));
            } else {
                throw new IllegalArgumentException("Cannot derive refund amount");
            }
        }
        return refundService.refund(orderId, refundAmount, reason);
    }
}

