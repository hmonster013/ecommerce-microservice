package org.de013.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.config.RabbitMQConfig;
import org.de013.notificationservice.entity.Notification;
import org.de013.notificationservice.entity.enums.Priority;
import org.de013.notificationservice.queue.NotificationMessage;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for managing notification message queues
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationQueueService {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Queue notification for delivery
     */
    public void queueNotification(Notification notification) {
        log.info("Queuing notification for delivery: id={}, channel={}, priority={}", 
                notification.getId(), notification.getChannel(), notification.getPriority());

        try {
            NotificationMessage message = createNotificationMessage(notification);
            
            // Determine routing strategy based on priority and scheduling
            if (notification.isScheduled()) {
                queueScheduledNotification(message);
            } else if (isPriorityMessage(notification.getPriority())) {
                queuePriorityNotification(message);
            } else {
                queueRegularNotification(message);
            }
            
            log.info("Notification queued successfully: id={}, routingKey={}", 
                    notification.getId(), message.getRoutingKey());
            
        } catch (Exception e) {
            log.error("Failed to queue notification: id={}, error={}", 
                    notification.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to queue notification: " + e.getMessage(), e);
        }
    }

    /**
     * Queue notification for retry
     */
    public void queueForRetry(Notification notification, int delaySeconds) {
        log.info("Queuing notification for retry: id={}, delay={}s, attempt={}", 
                notification.getId(), delaySeconds, notification.getRetryCount());

        try {
            NotificationMessage message = createNotificationMessage(notification);
            
            // Set message properties for delayed retry
            MessageProperties properties = new MessageProperties();
            properties.setPriority(message.getMessagePriority());
            properties.setExpiration(String.valueOf(delaySeconds * 1000)); // Convert to milliseconds
            properties.setHeader("x-retry-count", notification.getRetryCount());
            properties.setHeader("x-original-queue", message.getRoutingKey());
            
            // Send to retry queue
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NOTIFICATION_EXCHANGE,
                    RabbitMQConfig.NOTIFICATION_RETRY_ROUTING_KEY,
                    message,
                    msg -> {
                        msg.getMessageProperties().setPriority(message.getMessagePriority());
                        msg.getMessageProperties().setExpiration(String.valueOf(delaySeconds * 1000));
                        msg.getMessageProperties().setHeader("x-retry-count", notification.getRetryCount());
                        msg.getMessageProperties().setHeader("x-original-queue", message.getRoutingKey());
                        return msg;
                    }
            );
            
            log.info("Notification queued for retry successfully: id={}, delay={}s", 
                    notification.getId(), delaySeconds);
            
        } catch (Exception e) {
            log.error("Failed to queue notification for retry: id={}, error={}", 
                    notification.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to queue notification for retry: " + e.getMessage(), e);
        }
    }

    /**
     * Send notification to dead letter queue
     */
    public void sendToDeadLetterQueue(Notification notification, String reason) {
        log.warn("Sending notification to dead letter queue: id={}, reason={}", 
                notification.getId(), reason);

        try {
            NotificationMessage message = createNotificationMessage(notification);
            
            // Add failure information to metadata
            Map<String, Object> dlqMetadata = new HashMap<>(message.getMetadata() != null ? message.getMetadata() : new HashMap<>());
            dlqMetadata.put("dlq_reason", reason);
            dlqMetadata.put("dlq_timestamp", LocalDateTime.now().toString());
            dlqMetadata.put("final_retry_count", notification.getRetryCount());
            message.setMetadata(dlqMetadata);
            
            // Send to DLQ
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NOTIFICATION_DLX,
                    RabbitMQConfig.NOTIFICATION_DLQ_ROUTING_KEY,
                    message,
                    msg -> {
                        msg.getMessageProperties().setHeader("x-death-reason", reason);
                        msg.getMessageProperties().setHeader("x-original-queue", message.getRoutingKey());
                        return msg;
                    }
            );
            
            log.warn("Notification sent to dead letter queue: id={}", notification.getId());
            
        } catch (Exception e) {
            log.error("Failed to send notification to dead letter queue: id={}, error={}", 
                    notification.getId(), e.getMessage(), e);
        }
    }

    /**
     * Get queue depth for monitoring
     */
    public Map<String, Object> getQueueStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // Note: In production, you would use RabbitMQ Management API to get actual queue depths
            // This is a placeholder implementation
            stats.put("main_queue_depth", 0);
            stats.put("priority_queue_depth", 0);
            stats.put("retry_queue_depth", 0);
            stats.put("dlq_depth", 0);
            stats.put("email_queue_depth", 0);
            stats.put("sms_queue_depth", 0);
            stats.put("push_queue_depth", 0);
            stats.put("inapp_queue_depth", 0);
            stats.put("timestamp", LocalDateTime.now().toString());
            
        } catch (Exception e) {
            log.error("Failed to get queue stats: {}", e.getMessage(), e);
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }

    /**
     * Create notification message from notification entity
     */
    private NotificationMessage createNotificationMessage(Notification notification) {
        return NotificationMessage.builder()
                .notificationId(notification.getId())
                .userId(notification.getUserId())
                .type(notification.getType())
                .channel(notification.getChannel())
                .priority(notification.getPriority())
                .subject(notification.getSubject())
                .content(notification.getContent())
                .htmlContent(notification.getHtmlContent())
                .recipientAddress(notification.getRecipientAddress())
                .senderAddress(notification.getSenderAddress())
                .templateId(notification.getTemplateId())
                .templateVariables(notification.getTemplateVariables())
                .metadata(notification.getMetadata())
                .scheduledAt(notification.getScheduledAt())
                .expiresAt(notification.getExpiresAt())
                .retryCount(notification.getRetryCount())
                .maxRetryAttempts(notification.getMaxRetryAttempts())
                .correlationId(notification.getCorrelationId())
                .referenceType(notification.getReferenceType())
                .referenceId(notification.getReferenceId())
                .createdAt(notification.getCreatedAt())
                .queuedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Queue regular notification
     */
    private void queueRegularNotification(NotificationMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.NOTIFICATION_EXCHANGE,
                message.getRoutingKey(),
                message,
                msg -> {
                    msg.getMessageProperties().setPriority(message.getMessagePriority());
                    return msg;
                }
        );
    }

    /**
     * Queue priority notification
     */
    private void queuePriorityNotification(NotificationMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.NOTIFICATION_EXCHANGE,
                RabbitMQConfig.NOTIFICATION_PRIORITY_ROUTING_KEY,
                message,
                msg -> {
                    msg.getMessageProperties().setPriority(message.getMessagePriority());
                    msg.getMessageProperties().setHeader("x-priority", "high");
                    return msg;
                }
        );
    }

    /**
     * Queue scheduled notification with delay
     */
    private void queueScheduledNotification(NotificationMessage message) {
        long delaySeconds = message.getDelaySeconds();
        
        if (delaySeconds <= 0) {
            // Schedule time has passed, queue immediately
            queueRegularNotification(message);
            return;
        }
        
        // Use TTL and DLX for delayed delivery
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.NOTIFICATION_EXCHANGE,
                RabbitMQConfig.NOTIFICATION_RETRY_ROUTING_KEY, // Use retry queue for delay
                message,
                msg -> {
                    msg.getMessageProperties().setPriority(message.getMessagePriority());
                    msg.getMessageProperties().setExpiration(String.valueOf(delaySeconds * 1000));
                    msg.getMessageProperties().setHeader("x-scheduled", "true");
                    msg.getMessageProperties().setHeader("x-target-queue", message.getRoutingKey());
                    return msg;
                }
        );
    }

    /**
     * Check if notification has priority that requires special handling
     */
    private boolean isPriorityMessage(Priority priority) {
        return priority == Priority.CRITICAL || priority == Priority.URGENT;
    }
}
