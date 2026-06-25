package org.de013.orderservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.event.*;
import org.de013.orderservice.client.ProductCatalogClient;
import org.de013.orderservice.entity.Order;
import org.de013.orderservice.entity.ProcessedEvent;
import org.de013.orderservice.repository.OrderRepository;
import org.de013.orderservice.repository.ProcessedEventRepository;
import org.de013.orderservice.service.OrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final ObjectMapper objectMapper;
    private final OrderService orderService;
    private final ProcessedEventRepository processedEventRepository;
    private final OrderRepository orderRepository;
    private final ProductCatalogClient productCatalogClient;

    @KafkaListener(topics = Topics.PAYMENT_EVENTS, groupId = "order-service")
    @Transactional
    public void onPaymentEvent(String message) {
        log.info("Received payment event message: {}", message);
        try {
            EventEnvelope<?> env = objectMapper.readValue(message, EventEnvelope.class);
            if (processedEventRepository.existsById(env.getEventId())) {
                log.info("Skip duplicate event {}", env.getEventId());
                return;
            }

            switch (env.getEventType()) {
                case EventTypes.PAYMENT_SUCCEEDED -> {
                    PaymentSucceededPayload p = objectMapper.convertValue(env.getPayload(), PaymentSucceededPayload.class);
                    orderService.markOrderAsPaid(p.getOrderId(), p.getPaymentId(), p.getPaymentNumber());

                    // Fulfill reserved stock
                    Order order = orderRepository.findById(p.getOrderId())
                            .orElseThrow(() -> new org.de013.orderservice.exception.NotFoundException("Order not found"));
                    for (org.de013.orderservice.entity.OrderItem item : order.getOrderItems()) {
                        log.info("Fulfilling stock for product ID: {}, quantity: {}", item.getProductId(), item.getQuantity());
                        productCatalogClient.fulfillStock(item.getProductId(), item.getQuantity());
                    }
                }
                case EventTypes.PAYMENT_FAILED -> {
                    PaymentFailedPayload p = objectMapper.convertValue(env.getPayload(), PaymentFailedPayload.class);
                    orderService.markOrderPaymentFailed(p.getOrderId(), p.getFailureReason());

                    // Release reserved stock
                    Order order = orderRepository.findById(p.getOrderId())
                            .orElseThrow(() -> new org.de013.orderservice.exception.NotFoundException("Order not found"));
                    for (org.de013.orderservice.entity.OrderItem item : order.getOrderItems()) {
                        log.info("Releasing stock for product ID: {}, quantity: {}", item.getProductId(), item.getQuantity());
                        productCatalogClient.releaseStock(item.getProductId(), item.getQuantity());
                    }
                }
                default -> log.warn("Ignored event type {}", env.getEventType());
            }

            processedEventRepository.save(new ProcessedEvent(env.getEventId(), LocalDateTime.now()));
            log.info("Successfully processed and saved event {}", env.getEventId());
        } catch (Exception e) {
            log.error("Error processing payment event", e);
            throw new RuntimeException("Failed to process payment event", e);
        }
    }
}
