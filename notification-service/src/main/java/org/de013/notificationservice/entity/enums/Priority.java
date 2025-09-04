package org.de013.notificationservice.entity.enums;

import lombok.Getter;

/**
 * Priority Enumeration
 * Defines the priority levels for notifications
 */
@Getter
public enum Priority {
    
    LOW("Low", "Low priority notification", 1, 3600), // 1 hour delay acceptable
    NORMAL("Normal", "Normal priority notification", 2, 900), // 15 minutes delay acceptable
    HIGH("High", "High priority notification", 3, 300), // 5 minutes delay acceptable
    URGENT("Urgent", "Urgent priority notification", 4, 60), // 1 minute delay acceptable
    CRITICAL("Critical", "Critical priority notification", 5, 10); // 10 seconds delay acceptable

    private final String displayName;
    private final String description;
    private final int level;
    private final int maxDelaySeconds;

    Priority(String displayName, String description, int level, int maxDelaySeconds) {
        this.displayName = displayName;
        this.description = description;
        this.level = level;
        this.maxDelaySeconds = maxDelaySeconds;
    }

    /**
     * Check if this priority is higher than another priority
     */
    public boolean isHigherThan(Priority other) {
        return this.level > other.level;
    }

    /**
     * Check if this priority is lower than another priority
     */
    public boolean isLowerThan(Priority other) {
        return this.level < other.level;
    }

    /**
     * Check if this priority requires immediate processing
     */
    public boolean requiresImmediateProcessing() {
        return this == URGENT || this == CRITICAL;
    }

    /**
     * Get the queue priority weight for message queuing
     */
    public int getQueueWeight() {
        return level * 10;
    }

    /**
     * Get retry interval multiplier based on priority
     */
    public double getRetryMultiplier() {
        return switch (this) {
            case CRITICAL -> 0.5; // Retry faster for critical
            case URGENT -> 0.7;
            case HIGH -> 1.0;
            case NORMAL -> 1.5;
            case LOW -> 2.0; // Retry slower for low priority
        };
    }

    /**
     * Get maximum retry attempts based on priority
     */
    public int getMaxRetryAttempts() {
        return switch (this) {
            case CRITICAL -> 5;
            case URGENT -> 4;
            case HIGH -> 3;
            case NORMAL -> 2;
            case LOW -> 1;
        };
    }
}
