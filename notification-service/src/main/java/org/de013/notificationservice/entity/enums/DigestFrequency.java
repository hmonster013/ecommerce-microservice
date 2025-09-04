package org.de013.notificationservice.entity.enums;

import lombok.Getter;

/**
 * Digest Frequency Enumeration
 * Defines how often digest notifications should be sent
 */
@Getter
public enum DigestFrequency {
    
    DAILY("Daily", "Send digest once per day", 24),
    WEEKLY("Weekly", "Send digest once per week", 168),
    MONTHLY("Monthly", "Send digest once per month", 720); // Approximate hours in a month

    private final String displayName;
    private final String description;
    private final int intervalHours;

    DigestFrequency(String displayName, String description, int intervalHours) {
        this.displayName = displayName;
        this.description = description;
        this.intervalHours = intervalHours;
    }

    /**
     * Get next digest time based on current time
     */
    public java.time.LocalDateTime getNextDigestTime(java.time.LocalDateTime currentTime) {
        return switch (this) {
            case DAILY -> currentTime.plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
            case WEEKLY -> currentTime.plusWeeks(1).with(java.time.DayOfWeek.MONDAY).withHour(9).withMinute(0).withSecond(0).withNano(0);
            case MONTHLY -> currentTime.plusMonths(1).withDayOfMonth(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
        };
    }

    /**
     * Check if it's time to send digest
     */
    public boolean isTimeForDigest(java.time.LocalDateTime lastDigestTime, java.time.LocalDateTime currentTime) {
        if (lastDigestTime == null) {
            return true;
        }
        
        return switch (this) {
            case DAILY -> lastDigestTime.isBefore(currentTime.minusHours(24));
            case WEEKLY -> lastDigestTime.isBefore(currentTime.minusWeeks(1));
            case MONTHLY -> lastDigestTime.isBefore(currentTime.minusMonths(1));
        };
    }
}
