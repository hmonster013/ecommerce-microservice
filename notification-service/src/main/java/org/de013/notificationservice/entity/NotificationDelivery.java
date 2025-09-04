package org.de013.notificationservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.de013.notificationservice.entity.enums.DeliveryStatus;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Notification Delivery Entity
 * Tracks delivery attempts and status for each notification
 */
@Entity
@Table(name = "notification_deliveries", indexes = {
    @Index(name = "idx_delivery_notification_id", columnList = "notification_id"),
    @Index(name = "idx_delivery_channel", columnList = "channel"),
    @Index(name = "idx_delivery_status", columnList = "status"),
    @Index(name = "idx_delivery_attempt_at", columnList = "attempted_at"),
    @Index(name = "idx_delivery_delivered_at", columnList = "delivered_at"),
    @Index(name = "idx_delivery_external_id", columnList = "external_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(exclude = {"notification"})
@EqualsAndHashCode(callSuper = true, exclude = {"notification"})
public class NotificationDelivery extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id", nullable = false, foreignKey = @ForeignKey(name = "fk_delivery_notification"))
    private Notification notification;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Column(name = "recipient_address", nullable = false, length = 500)
    private String recipientAddress;

    @Column(name = "sender_address", length = 500)
    private String senderAddress;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private Integer maxAttempts = 3;

    @Column(name = "attempted_at")
    private LocalDateTime attemptedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "response_code", length = 50)
    private String responseCode;

    @Column(name = "response_message", columnDefinition = "TEXT")
    private String responseMessage;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "external_id", length = 255)
    private String externalId;

    @Column(name = "external_status", length = 100)
    private String externalStatus;

    @Column(name = "provider_name", length = 100)
    private String providerName;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_response", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> providerResponse = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "delivery_metadata", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> deliveryMetadata = new HashMap<>();

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "cost_cents")
    private Integer costCents;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "clicked_at")
    private LocalDateTime clickedAt;

    @Column(name = "bounced_at")
    private LocalDateTime bouncedAt;

    @Column(name = "bounce_reason", length = 500)
    private String bounceReason;

    @Column(name = "unsubscribed_at")
    private LocalDateTime unsubscribedAt;

    // Business methods

    /**
     * Check if delivery can be retried
     */
    public boolean canRetry() {
        return attemptCount < maxAttempts && 
               status.isCanRetry() && 
               !status.isTerminal();
    }

    /**
     * Increment attempt count and set next attempt time
     */
    public void incrementAttempt(int delaySeconds) {
        this.attemptCount++;
        this.attemptedAt = LocalDateTime.now();
        this.nextAttemptAt = LocalDateTime.now().plusSeconds(delaySeconds);
        this.status = DeliveryStatus.IN_PROGRESS;
    }

    /**
     * Mark delivery as successful
     */
    public void markAsDelivered(String externalId, String providerMessageId) {
        this.status = DeliveryStatus.SUCCESS;
        this.deliveredAt = LocalDateTime.now();
        this.externalId = externalId;
        this.providerMessageId = providerMessageId;
    }

    /**
     * Mark delivery as failed
     */
    public void markAsFailed(String errorMessage, String responseCode) {
        this.status = DeliveryStatus.FAILED;
        this.failedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
        this.responseCode = responseCode;
    }

    /**
     * Mark delivery as bounced
     */
    public void markAsBounced(String bounceReason) {
        this.status = DeliveryStatus.BOUNCED;
        this.bouncedAt = LocalDateTime.now();
        this.bounceReason = bounceReason;
    }

    /**
     * Mark delivery as opened (for email tracking)
     */
    public void markAsOpened() {
        this.openedAt = LocalDateTime.now();
    }

    /**
     * Mark delivery as clicked (for email tracking)
     */
    public void markAsClicked() {
        this.clickedAt = LocalDateTime.now();
    }

    /**
     * Check if delivery is in progress
     */
    public boolean isInProgress() {
        return status.isInProgress();
    }

    /**
     * Check if delivery is terminal (no further processing)
     */
    public boolean isTerminal() {
        return status.isTerminal();
    }

    /**
     * Get delivery duration in milliseconds
     */
    public Long getDeliveryDurationMs() {
        if (attemptedAt != null && deliveredAt != null) {
            return java.time.Duration.between(attemptedAt, deliveredAt).toMillis();
        }
        return null;
    }

    /**
     * Check if delivery was successful
     */
    public boolean isSuccessful() {
        return status.isSuccess();
    }

    /**
     * Check if delivery failed
     */
    public boolean isFailed() {
        return status.isFailure();
    }

    /**
     * Get next retry delay based on attempt count
     */
    public int getNextRetryDelaySeconds() {
        int baseDelay = status.getRetryDelaySeconds();
        // Exponential backoff: delay * (2 ^ attemptCount)
        return baseDelay * (int) Math.pow(2, attemptCount);
    }
}
