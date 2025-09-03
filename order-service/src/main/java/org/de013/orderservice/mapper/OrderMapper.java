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
        o.setUserId(req.getUserId());
        o.setOrderType(req.getOrderType());
        o.setOrderSource("WEB"); // Default for base version
        o.setShippingAddress(req.getShippingAddress());
        o.setBillingAddress(req.getEffectiveBillingAddress());
        o.setCustomerNotes(req.getCustomerNotes());
        o.setIsGift(false); // Simplified for base version
        o.setGiftMessage(null); // Simplified for base version
        // Totals will be computed later; initialize to zero with request currency
        if (req.getCurrency() != null) {
            o.setSubtotalAmount(Money.zero(req.getCurrency()));
            o.setTaxAmount(Money.zero(req.getCurrency()));
            o.setShippingAmount(Money.zero(req.getCurrency()));
            o.setDiscountAmount(Money.zero(req.getCurrency()));
            o.setTotalAmount(Money.zero(req.getCurrency()));
        }
        return o;
    }

    public void applyUpdate(Order order, UpdateOrderRequest req) {
        if (req.getShippingAddress() != null) order.setShippingAddress(req.getShippingAddress());
        if (req.getBillingAddress() != null) order.setBillingAddress(req.getBillingAddress());
        if (req.getCustomerNotes() != null) order.setCustomerNotes(req.getCustomerNotes());
        if (req.getInternalNotes() != null) order.setInternalNotes(req.getInternalNotes());
        if (req.getPriorityLevel() != null) order.setPriorityLevel(req.getPriorityLevel());
        if (req.getRequiresSpecialHandling() != null) order.setRequiresSpecialHandling(req.getRequiresSpecialHandling());
        if (req.getExpectedDeliveryDate() != null) order.setExpectedDeliveryDate(req.getExpectedDeliveryDate());
        if (req.getIsGift() != null) order.setIsGift(req.getIsGift());
        if (req.getGiftMessage() != null) order.setGiftMessage(req.getGiftMessage());
        // Pricing recalculation will be handled by OrderPricingService
    }

    public OrderResponse toResponse(Order o) {
        var items = o.getOrderItems();
        OrderResponse.OrderSummaryStats stats = OrderResponse.OrderSummaryStats.builder()
            .totalItems(items != null ? items.size() : 0)
            .totalQuantity(items != null ? items.stream().mapToInt(i -> i.getQuantity() != null ? i.getQuantity() : 0).sum() : 0)
            .uniqueProducts(items != null ? (int) items.stream().map(OrderItem::getProductId).distinct().count() : 0)
            .isPaid(o.isPaid())
            .isShipped(o.getStatus() == OrderStatus.SHIPPED || o.getStatus() == OrderStatus.DELIVERED || o.getStatus() == OrderStatus.COMPLETED)
            .isDelivered(o.isDelivered())
            .canBeCancelled(o.canBeCancelled())
            .canBeModified(o.canBeModified())
            .isFinalState(o.isFinalState())
            .orderAgeHours(o.getOrderAgeInHours())
            .build();

        OrderResponse resp = OrderResponse.builder()
            .id(o.getId())
            .orderNumber(o.getOrderNumber())
            .userId(o.getUserId())
            .status(o.getStatus())
            .orderType(o.getOrderType())
            .totalAmount(o.getTotalAmount())
            .subtotalAmount(o.getSubtotalAmount())
            .taxAmount(o.getTaxAmount())
            .shippingAmount(o.getShippingAmount())
            .discountAmount(o.getDiscountAmount())
            .shippingAddress(o.getShippingAddress())
            .billingAddress(o.getBillingAddress())
            .customerNotes(o.getCustomerNotes())
            .internalNotes(o.getInternalNotes())
            .orderSource(o.getOrderSource())
            .expectedDeliveryDate(o.getExpectedDeliveryDate())
            .actualDeliveryDate(o.getActualDeliveryDate())
            .confirmedAt(o.getConfirmedAt())
            .cancelledAt(o.getCancelledAt())
            .cancellationReason(o.getCancellationReason())
            .priorityLevel(o.getPriorityLevel())
            .requiresSpecialHandling(o.getRequiresSpecialHandling())
            .isGift(o.getIsGift())
            .giftMessage(o.getGiftMessage())
            .createdAt(o.getCreatedAt())
            .updatedAt(o.getUpdatedAt())
            .summaryStats(stats)
            .build();

        if (items != null) {
            resp.setOrderItems(items.stream().map(i -> OrderResponse.OrderItemResponse.builder()
                .id(i.getId())
                .productId(i.getProductId())
                .sku(i.getSku())
                .productName(i.getProductName())
                .productDescription(i.getProductDescription())
                .productCategory(i.getProductCategory())
                .productBrand(i.getProductBrand())
                .quantity(i.getQuantity())
                .unitPrice(i.getUnitPrice())
                .totalPrice(i.getTotalPrice())
                .discountAmount(i.getDiscountAmount())
                .taxAmount(i.getTaxAmount())
                .finalPrice(i.getFinalPrice())
                .priceIncludingTax(i.getPriceIncludingTax())
                .weight(i.getWeight())
                .weightUnit(i.getWeightUnit())
                .dimensions(i.getDimensions())
                .productImageUrl(i.getProductImageUrl())
                .variantInfo(i.getVariantInfo())
                .specialInstructions(i.getSpecialInstructions())
                .isGift(i.getIsGift())
                .giftWrapType(i.getGiftWrapType())
                .giftMessage(i.getGiftMessage())
                .requiresSpecialHandling(i.getRequiresSpecialHandling())
                .isFragile(i.getIsFragile())
                .isHazardous(i.getIsHazardous())
                .expectedDeliveryDate(i.getExpectedDeliveryDate())
                .actualDeliveryDate(i.getActualDeliveryDate())
                .status(i.getStatus())
                .discountPercentage(i.getDiscountPercentage())
                .totalWeight(i.getTotalWeight())
                .isDelivered(i.isDelivered())
                .isOverdue(i.isOverdue())
                .build()).collect(Collectors.toList()));
        }

        // Basic order mapping complete
        return resp;
    }

    public void ensureTotalsInitialized(Order o, String fallbackCurrency) {
        String currency = null;
        if (o.getTotalAmount() != null) currency = o.getTotalAmount().getCurrency();
        else if (o.getSubtotalAmount() != null) currency = o.getSubtotalAmount().getCurrency();
        else currency = fallbackCurrency != null ? fallbackCurrency : "USD";

        if (o.getSubtotalAmount() == null) o.setSubtotalAmount(Money.zero(currency));
        if (o.getTaxAmount() == null) o.setTaxAmount(Money.zero(currency));
        if (o.getShippingAmount() == null) o.setShippingAmount(Money.zero(currency));
        if (o.getDiscountAmount() == null) o.setDiscountAmount(Money.zero(currency));
        if (o.getTotalAmount() == null) o.setTotalAmount(Money.zero(currency));
    }
}

