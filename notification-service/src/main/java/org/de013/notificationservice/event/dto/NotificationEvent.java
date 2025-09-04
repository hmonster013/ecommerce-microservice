package org.de013.notificationservice.event.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.de013.notificationservice.entity.enums.DeliveryStatus;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.entity.enums.NotificationStatus;
import org.de013.notificationservice.entity.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Notification event DTO for publishing notification lifecycle events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {

    private Long notificationId;
    private Long userId;
    private NotificationType type;
    private NotificationChannel channel;
    private NotificationStatus status;
    private String subject;
    private String recipientAddress;
    private String senderAddress;
    private Long templateId;
    private String externalId;
    private String providerName;
    private DeliveryStatus deliveryStatus;
    private String errorMessage;
    private Long processingTimeMs;
    private Integer attemptCount;
    private String correlationId;
    private String referenceType;
    private String referenceId;
    private Map<String, Object> metadata;
    private String eventType; // NOTIFICATION_SENT, NOTIFICATION_DELIVERED, NOTIFICATION_FAILED, NOTIFICATION_READ
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sentAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deliveredAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime readAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventTimestamp;

    /**
     * Create notification sent event
     */
    public static NotificationEvent createSentEvent(Long notificationId, Long userId, 
                                                   NotificationType type, NotificationChannel channel,
                                                   String recipientAddress, String correlationId) {
        return NotificationEvent.builder()
                .notificationId(notificationId)
                .userId(userId)
                .type(type)
                .channel(channel)
                .status(NotificationStatus.SENT)
                .recipientAddress(recipientAddress)
                .correlationId(correlationId)
                .eventType("NOTIFICATION_SENT")
                .sentAt(LocalDateTime.now())
                .eventTimestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create notification delivered event
     */
    public static NotificationEvent createDeliveredEvent(Long notificationId, Long userId,
                                                        NotificationType type, NotificationChannel channel,
                                                        String recipientAddress, String externalId,
                                                        String providerName, Long processingTimeMs,
                                                        String correlationId) {
        return NotificationEvent.builder()
                .notificationId(notificationId)
                .userId(userId)
                .type(type)
                .channel(channel)
                .status(NotificationStatus.DELIVERED)
                .recipientAddress(recipientAddress)
                .externalId(externalId)
                .providerName(providerName)
                .deliveryStatus(DeliveryStatus.SUCCESS)
                .processingTimeMs(processingTimeMs)
                .correlationId(correlationId)
                .eventType("NOTIFICATION_DELIVERED")
                .deliveredAt(LocalDateTime.now())
                .eventTimestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create notification failed event
     */
    public static NotificationEvent createFailedEvent(Long notificationId, Long userId,
                                                     NotificationType type, NotificationChannel channel,
                                                     String recipientAddress, String errorMessage,
                                                     String providerName, Integer attemptCount,
                                                     String correlationId) {
        return NotificationEvent.builder()
                .notificationId(notificationId)
                .userId(userId)
                .type(type)
                .channel(channel)
                .status(NotificationStatus.FAILED)
                .recipientAddress(recipientAddress)
                .errorMessage(errorMessage)
                .providerName(providerName)
                .attemptCount(attemptCount)
                .correlationId(correlationId)
                .eventType("NOTIFICATION_FAILED")
                .eventTimestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create notification read event
     */
    public static NotificationEvent createReadEvent(Long notificationId, Long userId,
                                                   NotificationType type, NotificationChannel channel,
                                                   String correlationId) {
        return NotificationEvent.builder()
                .notificationId(notificationId)
                .userId(userId)
                .type(type)
                .channel(channel)
                .status(NotificationStatus.READ)
                .correlationId(correlationId)
                .eventType("NOTIFICATION_READ")
                .readAt(LocalDateTime.now())
                .eventTimestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Get routing key for event publishing
     */
    public String getRoutingKey() {
        return "notification.event." + eventType.toLowerCase() + "." + channel.name().toLowerCase();
    }

    /**
     * Check if event should be published
     */
    public boolean shouldPublish() {
        return eventType != null && notificationId != null && userId != null;
    }

    /**
     * Get event priority for message publishing
     */
    public int getEventPriority() {
        return switch (eventType) {
            case "NOTIFICATION_FAILED" -> 8;
            case "NOTIFICATION_DELIVERED" -> 6;
            case "NOTIFICATION_SENT" -> 4;
            case "NOTIFICATION_READ" -> 2;
            default -> 1;
        };
    }
}
