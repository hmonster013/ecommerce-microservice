package org.de013.notificationservice.entity.enums;

import lombok.Getter;

/**
 * Delivery Status Enumeration
 * Defines the status of notification delivery attempts
 */
@Getter
public enum DeliveryStatus {
    
    PENDING("Pending", "Delivery is pending", false, false, true),
    IN_PROGRESS("In Progress", "Delivery is in progress", false, false, true),
    SUCCESS("Success", "Delivery was successful", true, false, false),
    FAILED("Failed", "Delivery failed", false, true, true),
    BOUNCED("Bounced", "Delivery bounced", false, true, true),
    REJECTED("Rejected", "Delivery was rejected", false, true, true),
    TIMEOUT("Timeout", "Delivery timed out", false, true, true),
    CANCELLED("Cancelled", "Delivery was cancelled", false, true, false),
    EXPIRED("Expired", "Delivery expired", false, true, false),
    RATE_LIMITED("Rate Limited", "Delivery was rate limited", false, false, true),
    PROVIDER_ERROR("Provider Error", "External provider error", false, true, true),
    INVALID_RECIPIENT("Invalid Recipient", "Recipient address is invalid", false, true, false);

    private final String displayName;
    private final String description;
    private final boolean isSuccess;
    private final boolean isFailure;
    private final boolean canRetry;

    DeliveryStatus(String displayName, String description, boolean isSuccess, boolean isFailure, boolean canRetry) {
        this.displayName = displayName;
        this.description = description;
        this.isSuccess = isSuccess;
        this.isFailure = isFailure;
        this.canRetry = canRetry;
    }

    /**
     * Check if this status is terminal (no further processing)
     */
    public boolean isTerminal() {
        return isSuccess || (isFailure && !canRetry);
    }

    /**
     * Check if this status indicates the delivery is in progress
     */
    public boolean isInProgress() {
        return this == PENDING || this == IN_PROGRESS || this == RATE_LIMITED;
    }

    /**
     * Check if this status requires immediate attention
     */
    public boolean requiresAttention() {
        return this == PROVIDER_ERROR || this == TIMEOUT;
    }

    /**
     * Get retry delay in seconds based on status
     */
    public int getRetryDelaySeconds() {
        return switch (this) {
            case FAILED -> 60; // 1 minute
            case BOUNCED -> 300; // 5 minutes
            case REJECTED -> 600; // 10 minutes
            case TIMEOUT -> 120; // 2 minutes
            case RATE_LIMITED -> 180; // 3 minutes
            case PROVIDER_ERROR -> 240; // 4 minutes
            default -> 0;
        };
    }

    /**
     * Get maximum retry attempts for this status
     */
    public int getMaxRetryAttempts() {
        return switch (this) {
            case FAILED -> 3;
            case BOUNCED -> 2;
            case REJECTED -> 1;
            case TIMEOUT -> 3;
            case RATE_LIMITED -> 5;
            case PROVIDER_ERROR -> 2;
            default -> 0;
        };
    }
}
