package org.de013.notificationservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.entity.enums.NotificationStatus;
import org.de013.notificationservice.entity.enums.NotificationType;
import org.de013.notificationservice.entity.enums.Priority;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Notification Data Transfer Object
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Notification information")
public class NotificationDto {

    @Schema(description = "Notification ID", example = "1")
    private Long id;

    @Schema(description = "User ID", example = "123")
    private Long userId;

    @Schema(description = "Notification type", example = "ORDER_PLACED")
    private NotificationType type;

    @Schema(description = "Notification channel", example = "EMAIL")
    private NotificationChannel channel;

    @Schema(description = "Notification status", example = "SENT")
    private NotificationStatus status;

    @Schema(description = "Notification priority", example = "NORMAL")
    private Priority priority;

    @Schema(description = "Notification subject", example = "Your order has been placed")
    private String subject;

    @Schema(description = "Notification content", example = "Thank you for your order...")
    private String content;

    @Schema(description = "HTML content for rich notifications")
    private String htmlContent;

    @Schema(description = "Recipient address", example = "user@example.com")
    private String recipientAddress;

    @Schema(description = "Sender address", example = "noreply@company.com")
    private String senderAddress;

    @Schema(description = "Template ID used for this notification", example = "5")
    private Long templateId;

    @Schema(description = "Template variables used for rendering")
    private Map<String, Object> templateVariables;

    @Schema(description = "Additional metadata")
    private Map<String, Object> metadata;

    @Schema(description = "Scheduled delivery time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime scheduledAt;

    @Schema(description = "Time when notification was sent")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sentAt;

    @Schema(description = "Time when notification was delivered")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deliveredAt;

    @Schema(description = "Time when notification was read")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime readAt;

    @Schema(description = "Expiration time for the notification")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;

    @Schema(description = "Number of retry attempts", example = "0")
    private Integer retryCount;

    @Schema(description = "Maximum retry attempts allowed", example = "3")
    private Integer maxRetryAttempts;

    @Schema(description = "Next retry time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime nextRetryAt;

    @Schema(description = "Error message if delivery failed")
    private String errorMessage;

    @Schema(description = "External ID from provider")
    private String externalId;

    @Schema(description = "Correlation ID for tracking")
    private String correlationId;

    @Schema(description = "Reference type", example = "ORDER")
    private String referenceType;

    @Schema(description = "Reference ID", example = "ORD-123")
    private String referenceId;

    @Schema(description = "Creation timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @Schema(description = "Created by user")
    private String createdBy;

    @Schema(description = "Updated by user")
    private String updatedBy;

    // Helper methods

    /**
     * Check if notification is scheduled
     */
    public boolean isScheduled() {
        return scheduledAt != null && scheduledAt.isAfter(LocalDateTime.now());
    }

    /**
     * Check if notification has expired
     */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    /**
     * Check if notification can be retried
     */
    public boolean canRetry() {
        return retryCount != null && maxRetryAttempts != null && 
               retryCount < maxRetryAttempts && 
               status != null && status.allowsRetry() && 
               !isExpired();
    }

    /**
     * Check if notification is in terminal state
     */
    public boolean isTerminal() {
        return status != null && status.isTerminal();
    }

    /**
     * Check if notification indicates user engagement
     */
    public boolean hasUserEngagement() {
        return readAt != null || (status != null && status.indicatesEngagement());
    }
}
