package org.de013.orderservice.service.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.orderservice.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncTaskPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.order.messaging.exchange:order.exchange}")
    private String exchange;

    public void sendOrderProcessing(String orderNumber) {
        rabbitTemplate.convertAndSend(exchange, "order.processing", orderNumber);
        log.debug("Published to OrderProcessingQueue: {}", orderNumber);
    }

    public void sendEmail(Object payload) {
        rabbitTemplate.convertAndSend(exchange, "email.send", payload);
    }

    public void sendSms(Object payload) {
        rabbitTemplate.convertAndSend(exchange, "sms.send", payload);
    }

    public void sendAnalytics(Object payload) {
        rabbitTemplate.convertAndSend(exchange, "analytics.event", payload);
    }

    public void sendAudit(Object payload) {
        rabbitTemplate.convertAndSend(exchange, "audit.event", payload);
    }
}

