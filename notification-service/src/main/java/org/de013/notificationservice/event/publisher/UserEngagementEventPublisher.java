package org.de013.notificationservice.event.publisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.config.EventRabbitMQConfig;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.entity.enums.NotificationType;
import org.de013.notificationservice.event.dto.UserEngagementEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Publisher for user engagement events
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserEngagementEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Publish email opened event
     */
    public void publishEmailOpened(Long notificationId, Long userId, NotificationType notificationType,
                                  String userAgent, String ipAddress, String correlationId) {
        try {
            UserEngagementEvent event = UserEngagementEvent.createEmailOpenedEvent(
                    notificationId, userId, notificationType, userAgent, ipAddress, correlationId);

            publishEvent(event);
            
            log.info("Published email opened event: notificationId={}, userId={}", notificationId, userId);
            
        } catch (Exception e) {
            log.error("Error publishing email opened event: notificationId={}, error={}", 
                    notificationId, e.getMessage(), e);
        }
    }

    /**
     * Publish link clicked event
     */
    public void publishLinkClicked(Long notificationId, Long userId, NotificationType notificationType,
                                  NotificationChannel channel, String actionTarget, String userAgent,
                                  String ipAddress, String correlationId) {
        try {
            UserEngagementEvent event = UserEngagementEvent.createLinkClickedEvent(
                    notificationId, userId, notificationType, channel, actionTarget, 
                    userAgent, ipAddress, correlationId);

            publishEvent(event);
            
            log.info("Published link clicked event: notificationId={}, userId={}, target={}", 
                    notificationId, userId, actionTarget);
            
        } catch (Exception e) {
            log.error("Error publishing link clicked event: notificationId={}, error={}", 
                    notificationId, e.getMessage(), e);
        }
    }

    /**
     * Publish push notification opened event
     */
    public void publishPushOpened(Long notificationId, Long userId, NotificationType notificationType,
                                 String deviceType, String platform, String correlationId) {
        try {
            UserEngagementEvent event = UserEngagementEvent.createPushOpenedEvent(
                    notificationId, userId, notificationType, deviceType, platform, correlationId);

            publishEvent(event);
            
            log.info("Published push opened event: notificationId={}, userId={}, platform={}", 
                    notificationId, userId, platform);
            
        } catch (Exception e) {
            log.error("Error publishing push opened event: notificationId={}, error={}", 
                    notificationId, e.getMessage(), e);
        }
    }

    /**
     * Publish unsubscribe event
     */
    public void publishUnsubscribe(Long notificationId, Long userId, NotificationType notificationType,
                                  NotificationChannel channel, String correlationId) {
        try {
            UserEngagementEvent event = UserEngagementEvent.createUnsubscribeEvent(
                    notificationId, userId, notificationType, channel, correlationId);

            publishEvent(event);
            
            log.warn("Published unsubscribe event: notificationId={}, userId={}, channel={}", 
                    notificationId, userId, channel);
            
        } catch (Exception e) {
            log.error("Error publishing unsubscribe event: notificationId={}, error={}", 
                    notificationId, e.getMessage(), e);
        }
    }

    /**
     * Publish spam report event
     */
    public void publishSpamReport(Long notificationId, Long userId, NotificationType notificationType,
                                 NotificationChannel channel, String correlationId) {
        try {
            UserEngagementEvent event = UserEngagementEvent.createSpamReportEvent(
                    notificationId, userId, notificationType, channel, correlationId);

            publishEvent(event);
            
            log.warn("Published spam report event: notificationId={}, userId={}, channel={}", 
                    notificationId, userId, channel);
            
        } catch (Exception e) {
            log.error("Error publishing spam report event: notificationId={}, error={}", 
                    notificationId, e.getMessage(), e);
        }
    }

    /**
     * Publish notification dismissed event
     */
    public void publishNotificationDismissed(Long notificationId, Long userId, NotificationType notificationType,
                                           NotificationChannel channel, String correlationId) {
        try {
            UserEngagementEvent event = UserEngagementEvent.builder()
                    .notificationId(notificationId)
                    .userId(userId)
                    .notificationType(notificationType)
                    .channel(channel)
                    .engagementType("DISMISSED")
                    .actionType("DISMISS_CLICK")
                    .correlationId(correlationId)
                    .engagementTimestamp(LocalDateTime.now())
                    .build();

            publishEvent(event);
            
            log.debug("Published notification dismissed event: notificationId={}, userId={}", 
                    notificationId, userId);
            
        } catch (Exception e) {
            log.error("Error publishing notification dismissed event: notificationId={}, error={}", 
                    notificationId, e.getMessage(), e);
        }
    }

    /**
     * Publish custom engagement event
     */
    public void publishCustomEngagement(Long notificationId, Long userId, NotificationType notificationType,
                                       NotificationChannel channel, String engagementType, String actionType,
                                       String actionTarget, Map<String, Object> metadata, String correlationId) {
        try {
            UserEngagementEvent event = UserEngagementEvent.builder()
                    .notificationId(notificationId)
                    .userId(userId)
                    .notificationType(notificationType)
                    .channel(channel)
                    .engagementType(engagementType)
                    .actionType(actionType)
                    .actionTarget(actionTarget)
                    .metadata(metadata)
                    .correlationId(correlationId)
                    .engagementTimestamp(LocalDateTime.now())
                    .build();

            publishEvent(event);
            
            log.info("Published custom engagement event: notificationId={}, userId={}, type={}", 
                    notificationId, userId, engagementType);
            
        } catch (Exception e) {
            log.error("Error publishing custom engagement event: notificationId={}, error={}", 
                    notificationId, e.getMessage(), e);
        }
    }

    /**
     * Publish engagement event with timing information
     */
    public void publishEngagementWithTiming(Long notificationId, Long userId, NotificationType notificationType,
                                          NotificationChannel channel, String engagementType, String actionType,
                                          LocalDateTime notificationSentAt, String correlationId) {
        try {
            UserEngagementEvent event = UserEngagementEvent.builder()
                    .notificationId(notificationId)
                    .userId(userId)
                    .notificationType(notificationType)
                    .channel(channel)
                    .engagementType(engagementType)
                    .actionType(actionType)
                    .correlationId(correlationId)
                    .notificationSentAt(notificationSentAt)
                    .engagementTimestamp(LocalDateTime.now())
                    .build();

            publishEvent(event);
            
            Long delaySeconds = event.getEngagementDelaySeconds();
            log.info("Published engagement event with timing: notificationId={}, userId={}, type={}, delay={}s", 
                    notificationId, userId, engagementType, delaySeconds);
            
        } catch (Exception e) {
            log.error("Error publishing engagement event with timing: notificationId={}, error={}", 
                    notificationId, e.getMessage(), e);
        }
    }

    /**
     * Publish engagement event
     */
    private void publishEvent(UserEngagementEvent event) {
        if (!event.shouldPublish()) {
            log.warn("Engagement event should not be published: type={}, notificationId={}", 
                    event.getEngagementType(), event.getNotificationId());
            return;
        }

        try {
            rabbitTemplate.convertAndSend(
                    EventRabbitMQConfig.ENGAGEMENT_EVENTS_EXCHANGE,
                    event.getRoutingKey(),
                    event,
                    message -> {
                        message.getMessageProperties().setPriority(event.getEventPriority());
                        message.getMessageProperties().setHeader("engagementType", event.getEngagementType());
                        message.getMessageProperties().setHeader("notificationId", event.getNotificationId());
                        message.getMessageProperties().setHeader("userId", event.getUserId());
                        message.getMessageProperties().setHeader("channel", event.getChannel());
                        message.getMessageProperties().setHeader("isPositive", event.isPositiveEngagement());
                        message.getMessageProperties().setHeader("isNegative", event.isNegativeEngagement());
                        return message;
                    }
            );
            
            log.debug("Successfully published engagement event: type={}, routingKey={}, notificationId={}", 
                    event.getEngagementType(), event.getRoutingKey(), event.getNotificationId());
            
        } catch (Exception e) {
            log.error("Failed to publish engagement event: type={}, notificationId={}, error={}", 
                    event.getEngagementType(), event.getNotificationId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Publish bulk engagement events
     */
    public void publishBulkEngagementEvents(java.util.List<UserEngagementEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        log.info("Publishing {} engagement events in bulk", events.size());
        
        int successCount = 0;
        int failureCount = 0;
        
        for (UserEngagementEvent event : events) {
            try {
                publishEvent(event);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                log.error("Failed to publish bulk engagement event: type={}, notificationId={}, error={}", 
                        event.getEngagementType(), event.getNotificationId(), e.getMessage());
            }
        }
        
        log.info("Bulk engagement events published: success={}, failures={}", successCount, failureCount);
    }
}
