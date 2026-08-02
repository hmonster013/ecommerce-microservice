package org.de013.orderservice.mapper;

import org.de013.orderservice.dto.request.CreateOrderRequest;
import org.de013.orderservice.dto.request.UpdateOrderRequest;
import org.de013.orderservice.dto.response.OrderResponse;
import org.de013.orderservice.entity.Order;
import org.de013.orderservice.entity.OrderItem;
import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.entity.valueobject.Money;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class OrderMapper {

    public Order toEntity(CreateOrderRequest req) {
        Order o = new Order();
        o.setUserId(req.userId());
        o.setOrderType(req.orderType());
        o.setOrderSource("WEB"); // Default for base version
        o.setShippingAddress(req.shippingAddress());
        o.setBillingAddress(req.getEffectiveBillingAddress());
        o.setCustomerNotes(req.customerNotes());
        o.setIsGift(false); // Simplified for base version
        o.setGiftMessage(null); // Simplified for base version
        // Totals will be computed later; initialize to zero with request currency
        if (req.currency() != null) {
            o.setSubtotalAmount(Money.zero(req.currency()));
            o.setTaxAmount(Money.zero(req.currency()));
            o.setShippingAmount(Money.zero(req.currency()));
            o.setDiscountAmount(Money.zero(req.currency()));
            o.setTotalAmount(Money.zero(req.currency()));
        }
        return o;
    }

    public void applyUpdate(Order order, UpdateOrderRequest req) {
        if (req.shippingAddress() != null) order.setShippingAddress(req.shippingAddress());
        if (req.billingAddress() != null) order.setBillingAddress(req.billingAddress());
        if (req.customerNotes() != null) order.setCustomerNotes(req.customerNotes());
        if (req.internalNotes() != null) order.setInternalNotes(req.internalNotes());
        if (req.priorityLevel() != null) order.setPriorityLevel(req.priorityLevel());
        if (req.requiresSpecialHandling() != null)
            order.setRequiresSpecialHandling(req.requiresSpecialHandling());
        if (req.expectedDeliveryDate() != null) order.setExpectedDeliveryDate(req.expectedDeliveryDate());
        if (req.isGift() != null) order.setIsGift(req.isGift());
        if (req.giftMessage() != null) order.setGiftMessage(req.giftMessage());
        // Pricing recalculation will be handled by OrderPricingService
    }

    public OrderResponse toResponse(Order o) {
        var items = o.getOrderItems();
        OrderResponse.OrderSummaryStats stats = new OrderResponse.OrderSummaryStats(
                items != null ? items.size() : 0,
                items != null ? items.stream().mapToInt(i -> i.getQuantity() != null ? i.getQuantity() : 0).sum() : 0,
                items != null ? (int) items.stream().map(OrderItem::getProductId).distinct().count() : 0,
                o.getOrderAgeInHours(),
                null,
                null,
                null,
                o.isPaid(),
                o.getStatus() == OrderStatus.SHIPPED || o.getStatus() == OrderStatus.DELIVERED || o.getStatus() == OrderStatus.COMPLETED,
                o.isDelivered(),
                o.canBeCancelled(),
                o.canBeModified(),
                o.isFinalState(),
                null,
                null
        );

        var itemResponses = items != null ? items.stream().map(i -> new OrderResponse.OrderItemResponse(
                i.getId(),
                i.getProductId(),
                i.getSku(),
                i.getProductName(),
                i.getProductDescription(),
                i.getProductCategory(),
                i.getProductBrand(),
                i.getQuantity(),
                i.getUnitPrice(),
                i.getTotalPrice(),
                i.getDiscountAmount(),
                i.getTaxAmount(),
                i.getFinalPrice(),
                i.getPriceIncludingTax(),
                i.getWeight(),
                i.getWeightUnit(),
                i.getDimensions(),
                i.getProductImageUrl(),
                i.getVariantInfo(),
                i.getSpecialInstructions(),
                i.getIsGift(),
                i.getGiftWrapType(),
                i.getGiftMessage(),
                i.getRequiresSpecialHandling(),
                i.getIsFragile(),
                i.getIsHazardous(),
                i.getExpectedDeliveryDate(),
                i.getActualDeliveryDate(),
                i.getStatus(),
                i.getDiscountPercentage(),
                i.getTotalWeight(),
                i.isDelivered(),
                i.isOverdue()
        )).collect(Collectors.toList()) : null;

        return new OrderResponse(
                o.getId(),
                o.getOrderNumber(),
                o.getUserId(),
                o.getStatus(),
                o.getOrderType(),
                o.getTotalAmount(),
                o.getSubtotalAmount(),
                o.getTaxAmount(),
                o.getShippingAmount(),
                o.getDiscountAmount(),
                o.getShippingAddress(),
                o.getBillingAddress(),
                o.getCustomerNotes(),
                o.getInternalNotes(),
                o.getOrderSource(),
                o.getExpectedDeliveryDate(),
                o.getActualDeliveryDate(),
                o.getConfirmedAt(),
                o.getCancelledAt(),
                o.getCancellationReason(),
                o.getPriorityLevel(),
                o.getRequiresSpecialHandling(),
                o.getIsGift(),
                o.getGiftMessage(),
                o.getCreatedAt(),
                o.getUpdatedAt(),
                itemResponses,
                stats
        );
    }
}
