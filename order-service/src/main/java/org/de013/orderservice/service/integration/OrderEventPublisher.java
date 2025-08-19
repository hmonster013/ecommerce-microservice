package org.de013.orderservice.service.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.orderservice.config.RabbitMQConfig;
import org.de013.orderservice.dto.event.OrderEvents;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.order.messaging.exchange:order.exchange}")
    private String exchange;

    public void publishOrderCreated(OrderEvents.OrderCreatedEvent event) {
        rabbitTemplate.convertAndSend(exchange, RabbitMQConfig.RK_ORDER_CREATED, event);
        log.debug("Published OrderCreatedEvent: {}", event);
    }

    public void publishOrderStatusChanged(OrderEvents.OrderStatusChangedEvent event) {
        rabbitTemplate.convertAndSend(exchange, RabbitMQConfig.RK_ORDER_STATUS_CHANGED, event);
        log.debug("Published OrderStatusChangedEvent: {}", event);
    }

    public void publishOrderCancelled(OrderEvents.OrderCancelledEvent event) {
        rabbitTemplate.convertAndSend(exchange, RabbitMQConfig.RK_ORDER_CANCELLED, event);
        log.debug("Published OrderCancelledEvent: {}", event);
    }

    public void publishOrderShipped(OrderEvents.OrderShippedEvent event) {
        rabbitTemplate.convertAndSend(exchange, RabbitMQConfig.RK_ORDER_SHIPPED, event);
        log.debug("Published OrderShippedEvent: {}", event);
    }

    public void publishOrderDelivered(OrderEvents.OrderDeliveredEvent event) {
        rabbitTemplate.convertAndSend(exchange, RabbitMQConfig.RK_ORDER_DELIVERED, event);
        log.debug("Published OrderDeliveredEvent: {}", event);
    }
}

