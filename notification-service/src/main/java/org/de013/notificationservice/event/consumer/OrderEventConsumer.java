package org.de013.notificationservice.event.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.dto.CreateNotificationRequest;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.entity.enums.NotificationType;
import org.de013.notificationservice.entity.enums.Priority;
import org.de013.notificationservice.event.dto.OrderEvent;
import org.de013.notificationservice.service.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumer for order events to trigger notifications
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final NotificationService notificationService;

    /**
     * Handle order placed events
     */
    @RabbitListener(
        queues = "notification.order.events",
        containerFactory = "eventRabbitListenerContainerFactory"
    )
    public void handleOrderEvent(@Payload OrderEvent orderEvent, @Header Map<String, Object> headers) {
        log.info("Received order event: type={}, orderId={}, userId={}", 
                orderEvent.getEventType(), orderEvent.getOrderId(), orderEvent.getUserId());

        try {
            switch (orderEvent.getEventType()) {
                case "ORDER_PLACED" -> handleOrderPlaced(orderEvent);
                case "ORDER_CONFIRMED" -> handleOrderConfirmed(orderEvent);
                case "ORDER_SHIPPED" -> handleOrderShipped(orderEvent);
                case "ORDER_DELIVERED" -> handleOrderDelivered(orderEvent);
                case "ORDER_CANCELLED" -> handleOrderCancelled(orderEvent);
                default -> log.warn("Unknown order event type: {}", orderEvent.getEventType());
            }
            
            log.info("Successfully processed order event: type={}, orderId={}", 
                    orderEvent.getEventType(), orderEvent.getOrderId());
            
        } catch (Exception e) {
            log.error("Error processing order event: type={}, orderId={}, error={}", 
                    orderEvent.getEventType(), orderEvent.getOrderId(), e.getMessage(), e);
            throw e; // Re-throw to trigger retry or DLQ
        }
    }

    /**
     * Handle order placed event
     */
    private void handleOrderPlaced(OrderEvent orderEvent) {
        log.info("Processing order placed event: orderId={}", orderEvent.getOrderId());

        // Send order confirmation email
        CreateNotificationRequest emailRequest = CreateNotificationRequest.builder()
                .userId(orderEvent.getUserId())
                .type(NotificationType.ORDER_CONFIRMATION)
                .channel(NotificationChannel.EMAIL)
                .priority(Priority.NORMAL)
                .recipientAddress(orderEvent.getCustomerEmail())
                .subject("Order Confirmation - " + orderEvent.getOrderNumber())
                .templateId(getTemplateId("ORDER_CONFIRMATION_EMAIL"))
                .templateVariables(orderEvent.getTemplateVariables())
                .correlationId(orderEvent.getCorrelationId())
                .referenceType("ORDER")
                .referenceId(orderEvent.getOrderId().toString())
                .build();

        notificationService.createNotification(emailRequest);

        // Send SMS if phone number is available
        if (orderEvent.getCustomerPhone() != null && !orderEvent.getCustomerPhone().isEmpty()) {
            CreateNotificationRequest smsRequest = CreateNotificationRequest.builder()
                    .userId(orderEvent.getUserId())
                    .type(NotificationType.ORDER_CONFIRMATION)
                    .channel(NotificationChannel.SMS)
                    .priority(Priority.NORMAL)
                    .recipientAddress(orderEvent.getCustomerPhone())
                    .content("Your order " + orderEvent.getOrderNumber() + " has been confirmed. Total: " + 
                            orderEvent.getCurrency() + " " + orderEvent.getTotalAmount())
                    .correlationId(orderEvent.getCorrelationId())
                    .referenceType("ORDER")
                    .referenceId(orderEvent.getOrderId().toString())
                    .build();

            notificationService.createNotification(smsRequest);
        }

        // Send in-app notification
        CreateNotificationRequest inAppRequest = CreateNotificationRequest.builder()
                .userId(orderEvent.getUserId())
                .type(NotificationType.ORDER_CONFIRMATION)
                .channel(NotificationChannel.IN_APP)
                .priority(Priority.NORMAL)
                .subject("Order Confirmed")
                .content("Your order " + orderEvent.getOrderNumber() + " has been confirmed and is being processed.")
                .correlationId(orderEvent.getCorrelationId())
                .referenceType("ORDER")
                .referenceId(orderEvent.getOrderId().toString())
                .build();

        notificationService.createNotification(inAppRequest);
    }

    /**
     * Handle order confirmed event
     */
    private void handleOrderConfirmed(OrderEvent orderEvent) {
        log.info("Processing order confirmed event: orderId={}", orderEvent.getOrderId());

        CreateNotificationRequest request = CreateNotificationRequest.builder()
                .userId(orderEvent.getUserId())
                .type(NotificationType.ORDER_UPDATE)
                .channel(NotificationChannel.EMAIL)
                .priority(Priority.NORMAL)
                .recipientAddress(orderEvent.getCustomerEmail())
                .subject("Order Update - " + orderEvent.getOrderNumber())
                .templateId(getTemplateId("ORDER_UPDATE_EMAIL"))
                .templateVariables(orderEvent.getTemplateVariables())
                .correlationId(orderEvent.getCorrelationId())
                .referenceType("ORDER")
                .referenceId(orderEvent.getOrderId().toString())
                .build();

        notificationService.createNotification(request);
    }

    /**
     * Handle order shipped event
     */
    private void handleOrderShipped(OrderEvent orderEvent) {
        log.info("Processing order shipped event: orderId={}, trackingNumber={}", 
                orderEvent.getOrderId(), orderEvent.getTrackingNumber());

        // Send shipping notification email
        CreateNotificationRequest emailRequest = CreateNotificationRequest.builder()
                .userId(orderEvent.getUserId())
                .type(NotificationType.ORDER_SHIPPED)
                .channel(NotificationChannel.EMAIL)
                .priority(Priority.NORMAL)
                .recipientAddress(orderEvent.getCustomerEmail())
                .subject("Your Order Has Shipped - " + orderEvent.getOrderNumber())
                .templateId(getTemplateId("ORDER_SHIPPED_EMAIL"))
                .templateVariables(orderEvent.getTemplateVariables())
                .correlationId(orderEvent.getCorrelationId())
                .referenceType("ORDER")
                .referenceId(orderEvent.getOrderId().toString())
                .build();

        notificationService.createNotification(emailRequest);

        // Send push notification
        CreateNotificationRequest pushRequest = CreateNotificationRequest.builder()
                .userId(orderEvent.getUserId())
                .type(NotificationType.ORDER_SHIPPED)
                .channel(NotificationChannel.PUSH)
                .priority(Priority.NORMAL)
                .subject("Order Shipped")
                .content("Your order " + orderEvent.getOrderNumber() + " has been shipped. " +
                        (orderEvent.getTrackingNumber() != null ? "Tracking: " + orderEvent.getTrackingNumber() : ""))
                .correlationId(orderEvent.getCorrelationId())
                .referenceType("ORDER")
                .referenceId(orderEvent.getOrderId().toString())
                .build();

        notificationService.createNotification(pushRequest);
    }

    /**
     * Handle order delivered event
     */
    private void handleOrderDelivered(OrderEvent orderEvent) {
        log.info("Processing order delivered event: orderId={}", orderEvent.getOrderId());

        CreateNotificationRequest request = CreateNotificationRequest.builder()
                .userId(orderEvent.getUserId())
                .type(NotificationType.ORDER_DELIVERED)
                .channel(NotificationChannel.EMAIL)
                .priority(Priority.NORMAL)
                .recipientAddress(orderEvent.getCustomerEmail())
                .subject("Order Delivered - " + orderEvent.getOrderNumber())
                .templateId(getTemplateId("ORDER_DELIVERED_EMAIL"))
                .templateVariables(orderEvent.getTemplateVariables())
                .correlationId(orderEvent.getCorrelationId())
                .referenceType("ORDER")
                .referenceId(orderEvent.getOrderId().toString())
                .build();

        notificationService.createNotification(request);

        // Send in-app notification for feedback request
        CreateNotificationRequest feedbackRequest = CreateNotificationRequest.builder()
                .userId(orderEvent.getUserId())
                .type(NotificationType.FEEDBACK_REQUEST)
                .channel(NotificationChannel.IN_APP)
                .priority(Priority.LOW)
                .subject("How was your order?")
                .content("Your order " + orderEvent.getOrderNumber() + " has been delivered. We'd love to hear your feedback!")
                .correlationId(orderEvent.getCorrelationId())
                .referenceType("ORDER")
                .referenceId(orderEvent.getOrderId().toString())
                .scheduledAt(java.time.LocalDateTime.now().plusHours(24)) // Send after 24 hours
                .build();

        notificationService.createNotification(feedbackRequest);
    }

    /**
     * Handle order cancelled event
     */
    private void handleOrderCancelled(OrderEvent orderEvent) {
        log.info("Processing order cancelled event: orderId={}", orderEvent.getOrderId());

        CreateNotificationRequest request = CreateNotificationRequest.builder()
                .userId(orderEvent.getUserId())
                .type(NotificationType.ORDER_CANCELLED)
                .channel(NotificationChannel.EMAIL)
                .priority(Priority.HIGH)
                .recipientAddress(orderEvent.getCustomerEmail())
                .subject("Order Cancelled - " + orderEvent.getOrderNumber())
                .templateId(getTemplateId("ORDER_CANCELLED_EMAIL"))
                .templateVariables(orderEvent.getTemplateVariables())
                .correlationId(orderEvent.getCorrelationId())
                .referenceType("ORDER")
                .referenceId(orderEvent.getOrderId().toString())
                .build();

        notificationService.createNotification(request);
    }

    /**
     * Get template ID based on template name
     */
    private Long getTemplateId(String templateName) {
        // In a real implementation, this would look up template IDs from a service or cache
        return switch (templateName) {
            case "ORDER_CONFIRMATION_EMAIL" -> 1L;
            case "ORDER_UPDATE_EMAIL" -> 2L;
            case "ORDER_SHIPPED_EMAIL" -> 3L;
            case "ORDER_DELIVERED_EMAIL" -> 4L;
            case "ORDER_CANCELLED_EMAIL" -> 5L;
            default -> null;
        };
    }
}
