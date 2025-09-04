package org.de013.notificationservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.de013.notificationservice.entity.enums.NotificationStatus;
import org.de013.notificationservice.entity.enums.NotificationType;
import org.de013.notificationservice.entity.enums.Priority;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Notification Entity
 * Represents a notification that can be sent through various channels
 */
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notification_user_id", columnList = "user_id"),
    @Index(name = "idx_notification_type", columnList = "type"),
    @Index(name = "idx_notification_status", columnList = "status"),
    @Index(name = "idx_notification_channel", columnList = "channel"),
    @Index(name = "idx_notification_priority", columnList = "priority"),
    @Index(name = "idx_notification_scheduled_at", columnList = "scheduled_at"),
    @Index(name = "idx_notification_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(exclude = {"deliveries"})
@EqualsAndHashCode(callSuper = true, exclude = {"deliveries"})
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    @Builder.Default
    private Priority priority = Priority.NORMAL;

    @Column(name = "subject", length = 500)
    private String subject;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "html_content", columnDefinition = "TEXT")
    private String htmlContent;

    @Column(name = "recipient_address", nullable = false, length = 500)
    private String recipientAddress;

    @Column(name = "sender_address", length = 500)
    private String senderAddress;

    @Column(name = "template_id")
    private Long templateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "template_variables", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> templateVariables = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "max_retry_attempts", nullable = false)
    @Builder.Default
    private Integer maxRetryAttempts = 3;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "external_id", length = 255)
    private String externalId;

    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id", length = 255)
    private String referenceId;

    @OneToMany(mappedBy = "notification", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<NotificationDelivery> deliveries = new ArrayList<>();

    // Business methods

    /**
     * Check if notification is scheduled for future delivery
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
        return retryCount < maxRetryAttempts && 
               status.allowsRetry() && 
               !isExpired();
    }

    /**
     * Increment retry count and set next retry time
     */
    public void incrementRetry(int delaySeconds) {
        this.retryCount++;
        this.nextRetryAt = LocalDateTime.now().plusSeconds(delaySeconds);
        this.status = NotificationStatus.RETRY;
    }

    /**
     * Mark notification as sent
     */
    public void markAsSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    /**
     * Mark notification as delivered
     */
    public void markAsDelivered() {
        this.status = NotificationStatus.DELIVERED;
        this.deliveredAt = LocalDateTime.now();
    }

    /**
     * Mark notification as read
     */
    public void markAsRead() {
        this.status = NotificationStatus.READ;
        this.readAt = LocalDateTime.now();
    }

    /**
     * Mark notification as failed
     */
    public void markAsFailed(String errorMessage) {
        this.status = NotificationStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    /**
     * Check if notification is ready for delivery
     */
    public boolean isReadyForDelivery() {
        return !isExpired() && 
               (scheduledAt == null || !scheduledAt.isAfter(LocalDateTime.now())) &&
               (status == NotificationStatus.PENDING || status == NotificationStatus.RETRY);
    }
}
