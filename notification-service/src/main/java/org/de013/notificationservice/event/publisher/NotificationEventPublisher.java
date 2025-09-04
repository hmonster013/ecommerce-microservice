package org.de013.notificationservice.event.publisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.config.EventRabbitMQConfig;
import org.de013.notificationservice.entity.Notification;
import org.de013.notificationservice.entity.NotificationDelivery;
import org.de013.notificationservice.event.dto.NotificationEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Publisher for notification lifecycle events
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Publish notification sent event
     */
    public void publishNotificationSent(Notification notification) {
        try {
            NotificationEvent event = NotificationEvent.createSentEvent(
                    notification.getId(),
                    notification.getUserId(),
                    notification.getType(),
                    notification.getChannel(),
                    notification.getRecipientAddress(),
                    notification.getCorrelationId()
            );

            publishEvent(event);
            
            log.info("Published notification sent event: notificationId={}, userId={}, channel={}", 
                    notification.getId(), notification.getUserId(), notification.getChannel());
            
        } catch (Exception e) {
            log.error("Error publishing notification sent event: notificationId={}, error={}", 
                    notification.getId(), e.getMessage(), e);
        }
    }

    /**
     * Publish notification delivered event
     */
    public void publishNotificationDelivered(Notification notification, NotificationDelivery delivery) {
        try {
            NotificationEvent event = NotificationEvent.createDeliveredEvent(
                    notification.getId(),
                    notification.getUserId(),
                    notification.getType(),
                    notification.getChannel(),
                    notification.getRecipientAddress(),
                    delivery.getExternalId(),
                    delivery.getProviderName(),
                    delivery.getProcessingTimeMs(),
                    notification.getCorrelationId()
            );

            publishEvent(event);
            
            log.info("Published notification delivered event: notificationId={}, userId={}, channel={}, provider={}", 
                    notification.getId(), notification.getUserId(), notification.getChannel(), delivery.getProviderName());
            
        } catch (Exception e) {
            log.error("Error publishing notification delivered event: notificationId={}, error={}", 
                    notification.getId(), e.getMessage(), e);
        }
    }

    /**
     * Publish notification failed event
     */
    public void publishNotificationFailed(Notification notification, String errorMessage, 
                                        String providerName, Integer attemptCount) {
        try {
            NotificationEvent event = NotificationEvent.createFailedEvent(
                    notification.getId(),
                    notification.getUserId(),
                    notification.getType(),
                    notification.getChannel(),
                    notification.getRecipientAddress(),
                    errorMessage,
                    providerName,
                    attemptCount,
                    notification.getCorrelationId()
            );

            publishEvent(event);
            
            log.info("Published notification failed event: notificationId={}, userId={}, channel={}, error={}", 
                    notification.getId(), notification.getUserId(), notification.getChannel(), errorMessage);
            
        } catch (Exception e) {
            log.error("Error publishing notification failed event: notificationId={}, error={}", 
                    notification.getId(), e.getMessage(), e);
        }
    }

    /**
     * Publish notification read event
     */
    public void publishNotificationRead(Notification notification) {
        try {
            NotificationEvent event = NotificationEvent.createReadEvent(
                    notification.getId(),
                    notification.getUserId(),
                    notification.getType(),
                    notification.getChannel(),
                    notification.getCorrelationId()
            );

            publishEvent(event);
            
            log.info("Published notification read event: notificationId={}, userId={}, channel={}", 
                    notification.getId(), notification.getUserId(), notification.getChannel());
            
        } catch (Exception e) {
            log.error("Error publishing notification read event: notificationId={}, error={}", 
                    notification.getId(), e.getMessage(), e);
        }
    }

    /**
     * Publish notification event with retry logic
     */
    private void publishEvent(NotificationEvent event) {
        if (!event.shouldPublish()) {
            log.warn("Event should not be published: eventType={}, notificationId={}", 
                    event.getEventType(), event.getNotificationId());
            return;
        }

        try {
            rabbitTemplate.convertAndSend(
                    EventRabbitMQConfig.NOTIFICATION_EVENTS_EXCHANGE,
                    event.getRoutingKey(),
                    event,
                    message -> {
                        message.getMessageProperties().setPriority(event.getEventPriority());
                        message.getMessageProperties().setHeader("eventType", event.getEventType());
                        message.getMessageProperties().setHeader("notificationId", event.getNotificationId());
                        message.getMessageProperties().setHeader("userId", event.getUserId());
                        message.getMessageProperties().setHeader("channel", event.getChannel());
                        return message;
                    }
            );
            
            log.debug("Successfully published event: type={}, routingKey={}, notificationId={}", 
                    event.getEventType(), event.getRoutingKey(), event.getNotificationId());
            
        } catch (Exception e) {
            log.error("Failed to publish event: type={}, notificationId={}, error={}", 
                    event.getEventType(), event.getNotificationId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Publish bulk notification events
     */
    public void publishBulkNotificationEvents(java.util.List<NotificationEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        log.info("Publishing {} notification events in bulk", events.size());
        
        int successCount = 0;
        int failureCount = 0;
        
        for (NotificationEvent event : events) {
            try {
                publishEvent(event);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                log.error("Failed to publish bulk event: type={}, notificationId={}, error={}", 
                        event.getEventType(), event.getNotificationId(), e.getMessage());
            }
        }
        
        log.info("Bulk notification events published: success={}, failures={}", successCount, failureCount);
    }

    /**
     * Publish notification lifecycle summary event
     */
    public void publishNotificationLifecycleSummary(Notification notification, 
                                                   java.util.List<NotificationDelivery> deliveries) {
        try {
            // Create a summary event with all delivery attempts
            NotificationEvent summaryEvent = NotificationEvent.builder()
                    .notificationId(notification.getId())
                    .userId(notification.getUserId())
                    .type(notification.getType())
                    .channel(notification.getChannel())
                    .status(notification.getStatus())
                    .subject(notification.getSubject())
                    .recipientAddress(notification.getRecipientAddress())
                    .correlationId(notification.getCorrelationId())
                    .referenceType(notification.getReferenceType())
                    .referenceId(notification.getReferenceId())
                    .createdAt(notification.getCreatedAt())
                    .sentAt(notification.getSentAt())
                    .deliveredAt(notification.getDeliveredAt())
                    .readAt(notification.getReadAt())
                    .eventType("NOTIFICATION_LIFECYCLE_SUMMARY")
                    .eventTimestamp(java.time.LocalDateTime.now())
                    .metadata(java.util.Map.of(
                            "totalDeliveryAttempts", deliveries.size(),
                            "finalStatus", notification.getStatus().name(),
                            "totalProcessingTimeMs", deliveries.stream()
                                    .mapToLong(d -> d.getProcessingTimeMs() != null ? d.getProcessingTimeMs() : 0)
                                    .sum(),
                            "totalCostCents", deliveries.stream()
                                    .mapToLong(d -> d.getCostCents() != null ? d.getCostCents() : 0)
                                    .sum()
                    ))
                    .build();

            publishEvent(summaryEvent);
            
            log.info("Published notification lifecycle summary: notificationId={}, deliveryAttempts={}", 
                    notification.getId(), deliveries.size());
            
        } catch (Exception e) {
            log.error("Error publishing notification lifecycle summary: notificationId={}, error={}", 
                    notification.getId(), e.getMessage(), e);
        }
    }
}
