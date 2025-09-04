package org.de013.notificationservice.delivery.inapp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.delivery.DeliveryProvider;
import org.de013.notificationservice.delivery.DeliveryResult;
import org.de013.notificationservice.entity.Notification;
import org.de013.notificationservice.entity.NotificationDelivery;
import org.de013.notificationservice.entity.enums.DeliveryStatus;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * In-App notification delivery provider using WebSocket
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "notification.inapp.enabled", havingValue = "true", matchIfMissing = true)
public class InAppNotificationProvider implements DeliveryProvider {

    private final SimpMessagingTemplate messagingTemplate;
    private final InAppConfiguration inAppConfiguration;

    @Override
    public NotificationChannel getSupportedChannel() {
        return NotificationChannel.IN_APP;
    }

    @Override
    public boolean canHandle(Notification notification) {
        return notification.getChannel() == NotificationChannel.IN_APP &&
               notification.getUserId() != null;
    }

    @Override
    public DeliveryResult deliver(Notification notification) {
        long startTime = System.currentTimeMillis();
        String messageId = UUID.randomUUID().toString();
        
        log.info("Delivering in-app notification: id={}, userId={}", 
                notification.getId(), notification.getUserId());

        try {
            // Create in-app notification payload
            Map<String, Object> payload = createInAppPayload(notification, messageId);
            
            // Send to user-specific topic
            String destination = "/topic/user/" + notification.getUserId() + "/notifications";
            messagingTemplate.convertAndSend(destination, payload);
            
            // Also send to general user topic for real-time updates
            String userDestination = "/user/" + notification.getUserId() + "/queue/notifications";
            messagingTemplate.convertAndSend(userDestination, payload);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            log.info("In-app notification sent successfully: id={}, messageId={}, processingTime={}ms", 
                    notification.getId(), messageId, processingTime);
            
            return DeliveryResult.success(messageId, messageId, "200", processingTime);
            
        } catch (Exception e) {
            log.error("Failed to send in-app notification: id={}, error={}", 
                    notification.getId(), e.getMessage(), e);
            return DeliveryResult.failure(DeliveryStatus.FAILED, 
                    "Failed to send in-app notification: " + e.getMessage(), "500", e.getMessage());
        }
    }

    @Override
    public DeliveryResult checkStatus(NotificationDelivery delivery) {
        // For in-app notifications, delivery is immediate
        // Status is always successful if no exception was thrown during delivery
        return DeliveryResult.success(delivery.getExternalId(), delivery.getProviderMessageId());
    }

    @Override
    public String getProviderName() {
        return "IN_APP_WEBSOCKET";
    }

    @Override
    public boolean isAvailable() {
        try {
            // Check if WebSocket messaging is available
            return messagingTemplate != null;
        } catch (Exception e) {
            log.warn("In-app notification provider not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public int getRateLimit() {
        return inAppConfiguration.getRateLimit();
    }

    /**
     * Create in-app notification payload
     */
    private Map<String, Object> createInAppPayload(Notification notification, String messageId) {
        Map<String, Object> payload = new HashMap<>();
        
        // Basic notification data
        payload.put("id", notification.getId());
        payload.put("messageId", messageId);
        payload.put("type", notification.getType().name());
        payload.put("priority", notification.getPriority().name());
        payload.put("title", notification.getSubject());
        payload.put("message", notification.getContent());
        payload.put("timestamp", LocalDateTime.now().toString());
        
        // Additional data
        if (notification.getCorrelationId() != null) {
            payload.put("correlationId", notification.getCorrelationId());
        }
        
        if (notification.getReferenceType() != null) {
            payload.put("referenceType", notification.getReferenceType());
            payload.put("referenceId", notification.getReferenceId());
        }
        
        // Metadata
        if (notification.getMetadata() != null && !notification.getMetadata().isEmpty()) {
            payload.put("metadata", notification.getMetadata());
        }
        
        // UI configuration
        Map<String, Object> uiConfig = new HashMap<>();
        uiConfig.put("showBadge", inAppConfiguration.isShowBadge());
        uiConfig.put("autoHide", inAppConfiguration.isAutoHide());
        uiConfig.put("autoHideDelay", inAppConfiguration.getAutoHideDelaySeconds());
        uiConfig.put("playSound", inAppConfiguration.isPlaySound());
        uiConfig.put("soundFile", inAppConfiguration.getSoundFile());
        
        // Priority-based UI settings
        switch (notification.getPriority()) {
            case CRITICAL:
            case URGENT:
                uiConfig.put("persistent", true);
                uiConfig.put("autoHide", false);
                uiConfig.put("className", "notification-urgent");
                break;
            case HIGH:
                uiConfig.put("autoHideDelay", inAppConfiguration.getAutoHideDelaySeconds() * 2);
                uiConfig.put("className", "notification-high");
                break;
            case LOW:
                uiConfig.put("autoHideDelay", inAppConfiguration.getAutoHideDelaySeconds() / 2);
                uiConfig.put("className", "notification-low");
                break;
            default:
                uiConfig.put("className", "notification-normal");
                break;
        }
        
        payload.put("ui", uiConfig);
        
        // Actions (if configured)
        if (inAppConfiguration.isEnableActions()) {
            payload.put("actions", createNotificationActions(notification));
        }
        
        return payload;
    }

    /**
     * Create notification actions based on type
     */
    private Map<String, Object> createNotificationActions(Notification notification) {
        Map<String, Object> actions = new HashMap<>();
        
        switch (notification.getType()) {
            case ORDER_PLACED:
            case ORDER_CONFIRMED:
                actions.put("primary", Map.of(
                    "label", "View Order",
                    "action", "navigate",
                    "url", "/orders/" + notification.getReferenceId()
                ));
                break;
                
            case ORDER_SHIPPED:
                actions.put("primary", Map.of(
                    "label", "Track Package",
                    "action", "navigate",
                    "url", "/orders/" + notification.getReferenceId() + "/tracking"
                ));
                break;
                
            case PAYMENT_SUCCESS:
                actions.put("primary", Map.of(
                    "label", "View Receipt",
                    "action", "navigate",
                    "url", "/payments/" + notification.getReferenceId()
                ));
                break;
                
            case USER_REGISTRATION:
                actions.put("primary", Map.of(
                    "label", "Complete Profile",
                    "action", "navigate",
                    "url", "/profile/setup"
                ));
                break;
                
            default:
                // Generic actions
                actions.put("primary", Map.of(
                    "label", "View Details",
                    "action", "navigate",
                    "url", "/notifications/" + notification.getId()
                ));
                break;
        }
        
        // Always add dismiss action
        actions.put("secondary", Map.of(
            "label", "Dismiss",
            "action", "dismiss"
        ));
        
        return actions;
    }
}
