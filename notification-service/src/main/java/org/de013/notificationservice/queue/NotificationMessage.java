package org.de013.notificationservice.queue;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.entity.enums.NotificationType;
import org.de013.notificationservice.entity.enums.Priority;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Message object for notification queue
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {

    private Long notificationId;
    private Long userId;
    private NotificationType type;
    private NotificationChannel channel;
    private Priority priority;
    private String subject;
    private String content;
    private String htmlContent;
    private String recipientAddress;
    private String senderAddress;
    private Long templateId;
    private Map<String, Object> templateVariables;
    private Map<String, Object> metadata;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime scheduledAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;
    
    private Integer retryCount;
    private Integer maxRetryAttempts;
    private String correlationId;
    private String referenceType;
    private String referenceId;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime queuedAt;

    /**
     * Get message priority for RabbitMQ
     */
    public int getMessagePriority() {
        if (priority == null) {
            return 0;
        }
        return switch (priority) {
            case CRITICAL -> 10;
            case URGENT -> 8;
            case HIGH -> 6;
            case NORMAL -> 4;
            case LOW -> 2;
        };
    }

    /**
     * Get routing key based on channel and priority
     */
    public String getRoutingKey() {
        if (priority == Priority.CRITICAL || priority == Priority.URGENT) {
            return "notification.priority";
        }
        return "notification." + channel.name().toLowerCase();
    }

    /**
     * Check if message is expired
     */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    /**
     * Check if message should be processed immediately
     */
    public boolean isImmediate() {
        return scheduledAt == null || !scheduledAt.isAfter(LocalDateTime.now());
    }

    /**
     * Get delay in seconds for scheduled messages
     */
    public long getDelaySeconds() {
        if (scheduledAt == null || !scheduledAt.isAfter(LocalDateTime.now())) {
            return 0;
        }
        return java.time.Duration.between(LocalDateTime.now(), scheduledAt).getSeconds();
    }
}
