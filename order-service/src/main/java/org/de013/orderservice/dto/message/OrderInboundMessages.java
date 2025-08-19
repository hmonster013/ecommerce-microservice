package org.de013.orderservice.dto.message;

import lombok.*;

import java.math.BigDecimal;
import java.util.Map;

public class OrderInboundMessages {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaymentConfirmationMessage {
        private Long orderId;
        private String status; // AUTHORIZED, CAPTURED, REFUNDED, FAILED
        private String transactionId;
        private BigDecimal amount;
        private String currency;
        private Map<String, Object> metadata;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InventoryUpdateMessage {
        private Long orderId;
        private String action; // RESERVE, RELEASE
        private Map<String, Object> metadata;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShippingUpdateMessage {
        private Long orderId;
        private String status; // SHIPPED, DELIVERED, OUT_FOR_DELIVERY
        private String trackingNumber;
        private String carrier;
        private Map<String, Object> metadata;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NotificationMessage {
        private Long orderId;
        private String channel; // EMAIL, SMS, PUSH
        private String template;
        private Map<String, Object> data;
    }
}

