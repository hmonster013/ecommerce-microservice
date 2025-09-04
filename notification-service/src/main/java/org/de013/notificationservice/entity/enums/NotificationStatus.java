package org.de013.notificationservice.entity.enums;

import lombok.Getter;

/**
 * Notification Status Enumeration
 * Defines the various states a notification can be in during its lifecycle
 */
@Getter
public enum NotificationStatus {
    
    DRAFT("Draft", "Notification is in draft state", false, false),
    PENDING("Pending", "Notification is pending delivery", false, false),
    QUEUED("Queued", "Notification is queued for delivery", false, false),
    PROCESSING("Processing", "Notification is being processed", false, false),
    SENT("Sent", "Notification has been sent", true, false),
    DELIVERED("Delivered", "Notification has been delivered", true, false),
    READ("Read", "Notification has been read by recipient", true, false),
    FAILED("Failed", "Notification delivery failed", false, true),
    CANCELLED("Cancelled", "Notification was cancelled", false, true),
    EXPIRED("Expired", "Notification has expired", false, true),
    BOUNCED("Bounced", "Notification bounced back", false, true),
    REJECTED("Rejected", "Notification was rejected", false, true),
    RETRY("Retry", "Notification is being retried", false, false),
    DIGESTED("Digested", "Notification included in digest", false, true);

    private final String displayName;
    private final String description;
    private final boolean isSuccessState;
    private final boolean isFailureState;

    NotificationStatus(String displayName, String description, boolean isSuccessState, boolean isFailureState) {
        this.displayName = displayName;
        this.description = description;
        this.isSuccessState = isSuccessState;
        this.isFailureState = isFailureState;
    }

    /**
     * Check if this status indicates the notification is in progress
     */
    public boolean isInProgress() {
        return this == PENDING || this == QUEUED || this == PROCESSING || this == RETRY;
    }

    /**
     * Check if this status is a terminal state (no further processing)
     */
    public boolean isTerminal() {
        return isSuccessState || isFailureState;
    }

    /**
     * Check if this status allows retry
     */
    public boolean allowsRetry() {
        return this == FAILED || this == BOUNCED || this == REJECTED;
    }

    /**
     * Check if this status indicates user engagement
     */
    public boolean indicatesEngagement() {
        return this == READ;
    }

    /**
     * Get the next possible statuses from current status
     */
    public NotificationStatus[] getNextPossibleStatuses() {
        return switch (this) {
            case DRAFT -> new NotificationStatus[]{PENDING, CANCELLED};
            case PENDING -> new NotificationStatus[]{QUEUED, CANCELLED, EXPIRED};
            case QUEUED -> new NotificationStatus[]{PROCESSING, CANCELLED, EXPIRED};
            case PROCESSING -> new NotificationStatus[]{SENT, FAILED, CANCELLED};
            case SENT -> new NotificationStatus[]{DELIVERED, FAILED, BOUNCED};
            case DELIVERED -> new NotificationStatus[]{READ};
            case FAILED -> new NotificationStatus[]{RETRY, CANCELLED};
            case RETRY -> new NotificationStatus[]{PROCESSING, CANCELLED, EXPIRED};
            case BOUNCED -> new NotificationStatus[]{RETRY, CANCELLED};
            case REJECTED -> new NotificationStatus[]{RETRY, CANCELLED};
            default -> new NotificationStatus[]{};
        };
    }
}
