package org.de013.notificationservice.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.entity.Notification;
import org.de013.notificationservice.repository.NotificationRepository;
import org.de013.notificationservice.service.DeliveryService;
import org.de013.notificationservice.service.NotificationQueueService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * RabbitMQ listeners for notification queues
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationQueueListener {

    private final DeliveryService deliveryService;
    private final NotificationRepository notificationRepository;
    private final NotificationQueueService queueService;

    /**
     * Process regular notifications
     */
    @RabbitListener(queues = "notification.delivery.queue")
    public void processRegularNotification(@Payload NotificationMessage message,
                                         @Header Map<String, Object> headers) {
        log.info("Processing regular notification: id={}, channel={}", 
                message.getNotificationId(), message.getChannel());
        
        processNotificationMessage(message, headers, "regular");
    }

    /**
     * Process priority notifications
     */
    @RabbitListener(queues = "notification.delivery.priority.queue")
    public void processPriorityNotification(@Payload NotificationMessage message,
                                          @Header Map<String, Object> headers) {
        log.info("Processing priority notification: id={}, priority={}", 
                message.getNotificationId(), message.getPriority());
        
        processNotificationMessage(message, headers, "priority");
    }

    /**
     * Process email notifications
     */
    @RabbitListener(queues = "notification.email.queue")
    public void processEmailNotification(@Payload NotificationMessage message,
                                       @Header Map<String, Object> headers) {
        log.info("Processing email notification: id={}, recipient={}", 
                message.getNotificationId(), maskEmail(message.getRecipientAddress()));
        
        processNotificationMessage(message, headers, "email");
    }

    /**
     * Process SMS notifications
     */
    @RabbitListener(queues = "notification.sms.queue")
    public void processSmsNotification(@Payload NotificationMessage message,
                                     @Header Map<String, Object> headers) {
        log.info("Processing SMS notification: id={}, recipient={}", 
                message.getNotificationId(), maskPhone(message.getRecipientAddress()));
        
        processNotificationMessage(message, headers, "sms");
    }

    /**
     * Process push notifications
     */
    @RabbitListener(queues = "notification.push.queue")
    public void processPushNotification(@Payload NotificationMessage message,
                                      @Header Map<String, Object> headers) {
        log.info("Processing push notification: id={}, recipient={}", 
                message.getNotificationId(), maskToken(message.getRecipientAddress()));
        
        processNotificationMessage(message, headers, "push");
    }

    /**
     * Process in-app notifications
     */
    @RabbitListener(queues = "notification.inapp.queue")
    public void processInAppNotification(@Payload NotificationMessage message,
                                       @Header Map<String, Object> headers) {
        log.info("Processing in-app notification: id={}, userId={}", 
                message.getNotificationId(), message.getUserId());
        
        processNotificationMessage(message, headers, "inapp");
    }

    /**
     * Process dead letter queue messages for monitoring and alerting
     */
    @RabbitListener(queues = "notification.delivery.dlq")
    public void processDeadLetterMessage(@Payload NotificationMessage message,
                                       @Header Map<String, Object> headers) {
        log.error("Processing dead letter notification: id={}, reason={}", 
                message.getNotificationId(), 
                message.getMetadata() != null ? message.getMetadata().get("dlq_reason") : "unknown");
        
        // Log for monitoring and alerting
        logDeadLetterMessage(message, headers);
        
        // Could implement additional logic here:
        // - Send alerts to monitoring systems
        // - Store in separate failed notifications table
        // - Trigger manual review process
    }

    /**
     * Common notification processing logic
     */
    private void processNotificationMessage(NotificationMessage message, Map<String, Object> headers, String queueType) {
        try {
            // Check if message is expired
            if (message.isExpired()) {
                log.warn("Notification message expired: id={}, expiresAt={}", 
                        message.getNotificationId(), message.getExpiresAt());
                return;
            }

            // Get notification from database
            Optional<Notification> notificationOpt = notificationRepository.findById(message.getNotificationId());
            if (notificationOpt.isEmpty()) {
                log.error("Notification not found in database: id={}", message.getNotificationId());
                return;
            }

            Notification notification = notificationOpt.get();
            
            // Check if notification is still valid for processing
            if (!notification.isReadyForDelivery()) {
                log.warn("Notification not ready for delivery: id={}, status={}", 
                        notification.getId(), notification.getStatus());
                return;
            }

            // Process delivery
            deliveryService.deliverNotification(notification);
            
            log.info("Notification processed successfully from {} queue: id={}", 
                    queueType, message.getNotificationId());
            
        } catch (Exception e) {
            log.error("Error processing notification from {} queue: id={}, error={}", 
                    queueType, message.getNotificationId(), e.getMessage(), e);
            
            // Handle retry logic
            handleProcessingError(message, headers, e);
        }
    }

    /**
     * Handle processing errors and retry logic
     */
    private void handleProcessingError(NotificationMessage message, Map<String, Object> headers, Exception error) {
        try {
            // Get retry count from headers
            Integer retryCount = (Integer) headers.get("x-retry-count");
            if (retryCount == null) {
                retryCount = 0;
            }

            // Check if we should retry
            if (retryCount < message.getMaxRetryAttempts()) {
                log.info("Retrying notification: id={}, attempt={}/{}", 
                        message.getNotificationId(), retryCount + 1, message.getMaxRetryAttempts());
                
                // Get notification and queue for retry
                Optional<Notification> notificationOpt = notificationRepository.findById(message.getNotificationId());
                if (notificationOpt.isPresent()) {
                    Notification notification = notificationOpt.get();
                    notification.setRetryCount(retryCount + 1);
                    notificationRepository.save(notification);
                    
                    // Calculate retry delay (exponential backoff)
                    int delaySeconds = calculateRetryDelay(retryCount + 1);
                    queueService.queueForRetry(notification, delaySeconds);
                }
            } else {
                log.error("Max retry attempts exceeded for notification: id={}, sending to DLQ", 
                        message.getNotificationId());
                
                // Send to dead letter queue
                Optional<Notification> notificationOpt = notificationRepository.findById(message.getNotificationId());
                if (notificationOpt.isPresent()) {
                    queueService.sendToDeadLetterQueue(notificationOpt.get(), 
                            "Max retry attempts exceeded: " + error.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("Error handling processing error for notification: id={}, error={}", 
                    message.getNotificationId(), e.getMessage(), e);
        }
    }

    /**
     * Log dead letter message for monitoring
     */
    private void logDeadLetterMessage(NotificationMessage message, Map<String, Object> headers) {
        log.error("DEAD_LETTER_NOTIFICATION: id={}, userId={}, channel={}, type={}, reason={}, headers={}", 
                message.getNotificationId(),
                message.getUserId(),
                message.getChannel(),
                message.getType(),
                message.getMetadata() != null ? message.getMetadata().get("dlq_reason") : "unknown",
                headers);
    }

    /**
     * Calculate retry delay with exponential backoff
     */
    private int calculateRetryDelay(int retryCount) {
        // Base delay of 60 seconds, exponential backoff with max of 30 minutes
        int baseDelay = 60;
        int delay = baseDelay * (int) Math.pow(2, retryCount - 1);
        return Math.min(delay, 1800); // Max 30 minutes
    }

    /**
     * Mask email address for logging
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "****";
        String[] parts = email.split("@");
        return parts[0].substring(0, Math.min(2, parts[0].length())) + "****@" + parts[1];
    }

    /**
     * Mask phone number for logging
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "****";
        return phone.substring(0, 2) + "****" + phone.substring(phone.length() - 2);
    }

    /**
     * Mask device token for logging
     */
    private String maskToken(String token) {
        if (token == null || token.length() < 8) return "****";
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }
}
