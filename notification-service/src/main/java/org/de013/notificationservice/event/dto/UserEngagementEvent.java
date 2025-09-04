package org.de013.notificationservice.event.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.entity.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * User engagement event DTO for tracking user interactions with notifications
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEngagementEvent {

    private Long notificationId;
    private Long userId;
    private NotificationType notificationType;
    private NotificationChannel channel;
    private String engagementType; // OPENED, CLICKED, DISMISSED, UNSUBSCRIBED, MARKED_AS_SPAM
    private String actionType; // BUTTON_CLICK, LINK_CLICK, EMAIL_OPEN, PUSH_OPEN, etc.
    private String actionTarget; // URL, button ID, etc.
    private String userAgent;
    private String ipAddress;
    private String deviceType; // MOBILE, DESKTOP, TABLET
    private String platform; // IOS, ANDROID, WEB
    private Map<String, Object> metadata;
    private String correlationId;
    private String sessionId;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime engagementTimestamp;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime notificationSentAt;

    /**
     * Create email opened event
     */
    public static UserEngagementEvent createEmailOpenedEvent(Long notificationId, Long userId,
                                                            NotificationType notificationType,
                                                            String userAgent, String ipAddress,
                                                            String correlationId) {
        return UserEngagementEvent.builder()
                .notificationId(notificationId)
                .userId(userId)
                .notificationType(notificationType)
                .channel(NotificationChannel.EMAIL)
                .engagementType("OPENED")
                .actionType("EMAIL_OPEN")
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .correlationId(correlationId)
                .engagementTimestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create link clicked event
     */
    public static UserEngagementEvent createLinkClickedEvent(Long notificationId, Long userId,
                                                            NotificationType notificationType,
                                                            NotificationChannel channel,
                                                            String actionTarget, String userAgent,
                                                            String ipAddress, String correlationId) {
        return UserEngagementEvent.builder()
                .notificationId(notificationId)
                .userId(userId)
                .notificationType(notificationType)
                .channel(channel)
                .engagementType("CLICKED")
                .actionType("LINK_CLICK")
                .actionTarget(actionTarget)
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .correlationId(correlationId)
                .engagementTimestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create push notification opened event
     */
    public static UserEngagementEvent createPushOpenedEvent(Long notificationId, Long userId,
                                                           NotificationType notificationType,
                                                           String deviceType, String platform,
                                                           String correlationId) {
        return UserEngagementEvent.builder()
                .notificationId(notificationId)
                .userId(userId)
                .notificationType(notificationType)
                .channel(NotificationChannel.PUSH)
                .engagementType("OPENED")
                .actionType("PUSH_OPEN")
                .deviceType(deviceType)
                .platform(platform)
                .correlationId(correlationId)
                .engagementTimestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create unsubscribe event
     */
    public static UserEngagementEvent createUnsubscribeEvent(Long notificationId, Long userId,
                                                            NotificationType notificationType,
                                                            NotificationChannel channel,
                                                            String correlationId) {
        return UserEngagementEvent.builder()
                .notificationId(notificationId)
                .userId(userId)
                .notificationType(notificationType)
                .channel(channel)
                .engagementType("UNSUBSCRIBED")
                .actionType("UNSUBSCRIBE_CLICK")
                .correlationId(correlationId)
                .engagementTimestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create spam report event
     */
    public static UserEngagementEvent createSpamReportEvent(Long notificationId, Long userId,
                                                           NotificationType notificationType,
                                                           NotificationChannel channel,
                                                           String correlationId) {
        return UserEngagementEvent.builder()
                .notificationId(notificationId)
                .userId(userId)
                .notificationType(notificationType)
                .channel(channel)
                .engagementType("MARKED_AS_SPAM")
                .actionType("SPAM_REPORT")
                .correlationId(correlationId)
                .engagementTimestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Get routing key for event publishing
     */
    public String getRoutingKey() {
        return "user.engagement." + engagementType.toLowerCase() + "." + channel.name().toLowerCase();
    }

    /**
     * Check if event should be published
     */
    public boolean shouldPublish() {
        return engagementType != null && notificationId != null && userId != null;
    }

    /**
     * Get event priority for message publishing
     */
    public int getEventPriority() {
        return switch (engagementType) {
            case "MARKED_AS_SPAM", "UNSUBSCRIBED" -> 8;
            case "CLICKED" -> 6;
            case "OPENED" -> 4;
            case "DISMISSED" -> 2;
            default -> 1;
        };
    }

    /**
     * Calculate engagement delay (time between notification sent and engagement)
     */
    public Long getEngagementDelaySeconds() {
        if (notificationSentAt != null && engagementTimestamp != null) {
            return java.time.Duration.between(notificationSentAt, engagementTimestamp).getSeconds();
        }
        return null;
    }

    /**
     * Check if this is a positive engagement
     */
    public boolean isPositiveEngagement() {
        return "OPENED".equals(engagementType) || "CLICKED".equals(engagementType);
    }

    /**
     * Check if this is a negative engagement
     */
    public boolean isNegativeEngagement() {
        return "MARKED_AS_SPAM".equals(engagementType) || "UNSUBSCRIBED".equals(engagementType);
    }
}
