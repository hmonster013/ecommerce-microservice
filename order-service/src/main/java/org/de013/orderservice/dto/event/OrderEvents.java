package org.de013.orderservice.dto.event;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Order Events DTOs for publishing to RabbitMQ
 */
public class OrderEvents {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderCreatedEvent {
        private Long orderId;
        private String orderNumber;
        private Long userId;
        private String currency;
        private LocalDateTime createdAt;
        private Map<String, Object> metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderStatusChangedEvent {
        private Long orderId;
        private String orderNumber;
        private String previousStatus;
        private String newStatus;
        private LocalDateTime changedAt;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderCancelledEvent {
        private Long orderId;
        private String orderNumber;
        private String reason;
        private LocalDateTime cancelledAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderShippedEvent {
        private Long orderId;
        private String orderNumber;
        private String trackingNumber;
        private String carrier;
        private LocalDateTime shippedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderDeliveredEvent {
        private Long orderId;
        private String orderNumber;
        private LocalDateTime deliveredAt;
    }
}

