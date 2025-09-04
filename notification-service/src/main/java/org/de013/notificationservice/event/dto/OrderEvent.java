package org.de013.notificationservice.event.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Order event DTO for notification processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {

    private Long orderId;
    private Long userId;
    private String orderNumber;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private String currency;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private ShippingAddress shippingAddress;
    private List<OrderItem> items;
    private Map<String, Object> metadata;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime orderDate;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime shippedDate;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deliveredDate;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime estimatedDeliveryDate;
    
    private String trackingNumber;
    private String shippingCarrier;
    private String eventType; // ORDER_PLACED, ORDER_SHIPPED, ORDER_DELIVERED, ORDER_CANCELLED
    private String correlationId;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventTimestamp;

    public enum OrderStatus {
        PENDING,
        CONFIRMED,
        PROCESSING,
        SHIPPED,
        DELIVERED,
        CANCELLED,
        REFUNDED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItem {
        private Long productId;
        private String productName;
        private String productSku;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
        private String imageUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShippingAddress {
        private String fullName;
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String state;
        private String postalCode;
        private String country;
        private String phoneNumber;
    }

    /**
     * Get notification template variables
     */
    public Map<String, Object> getTemplateVariables() {
        Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("orderId", orderId);
        variables.put("orderNumber", orderNumber);
        variables.put("customerName", customerName);
        variables.put("totalAmount", totalAmount);
        variables.put("currency", currency);
        variables.put("orderDate", orderDate);
        variables.put("shippedDate", shippedDate);
        variables.put("deliveredDate", deliveredDate);
        variables.put("estimatedDeliveryDate", estimatedDeliveryDate);
        variables.put("trackingNumber", trackingNumber != null ? trackingNumber : "");
        variables.put("shippingCarrier", shippingCarrier != null ? shippingCarrier : "");
        variables.put("itemCount", items != null ? items.size() : 0);
        variables.put("shippingAddress", shippingAddress);
        return variables;
    }

    /**
     * Get notification priority based on order status
     */
    public String getNotificationPriority() {
        return switch (status) {
            case CANCELLED, REFUNDED -> "HIGH";
            case DELIVERED -> "NORMAL";
            case SHIPPED -> "NORMAL";
            case CONFIRMED -> "NORMAL";
            default -> "LOW";
        };
    }

    /**
     * Get notification type based on event
     */
    public String getNotificationType() {
        return switch (eventType) {
            case "ORDER_PLACED" -> "ORDER_CONFIRMATION";
            case "ORDER_SHIPPED" -> "ORDER_SHIPPED";
            case "ORDER_DELIVERED" -> "ORDER_DELIVERED";
            case "ORDER_CANCELLED" -> "ORDER_CANCELLED";
            default -> "ORDER_UPDATE";
        };
    }
}
