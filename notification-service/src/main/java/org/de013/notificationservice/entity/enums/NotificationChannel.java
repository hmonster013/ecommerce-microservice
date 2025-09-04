package org.de013.notificationservice.entity.enums;

import lombok.Getter;

/**
 * Notification Channel Enumeration
 * Defines different channels through which notifications can be delivered
 */
@Getter
public enum NotificationChannel {
    
    EMAIL("Email", "Email notification delivery", "email", true, 100),
    SMS("SMS", "SMS notification delivery", "sms", true, 50),
    PUSH("Push Notification", "Mobile push notification delivery", "push", true, 200),
    IN_APP("In-App", "In-application notification delivery", "in_app", false, 500),
    WEBHOOK("Webhook", "Webhook notification delivery", "webhook", true, 10),
    SLACK("Slack", "Slack notification delivery", "slack", true, 20),
    TEAMS("Microsoft Teams", "Microsoft Teams notification delivery", "teams", true, 20),
    DISCORD("Discord", "Discord notification delivery", "discord", true, 20);

    private final String displayName;
    private final String description;
    private final String code;
    private final boolean requiresExternalProvider;
    private final int defaultRateLimit; // messages per minute

    NotificationChannel(String displayName, String description, String code, 
                       boolean requiresExternalProvider, int defaultRateLimit) {
        this.displayName = displayName;
        this.description = description;
        this.code = code;
        this.requiresExternalProvider = requiresExternalProvider;
        this.defaultRateLimit = defaultRateLimit;
    }

    /**
     * Check if this channel supports rich content (HTML, images, etc.)
     */
    public boolean supportsRichContent() {
        return this == EMAIL || this == IN_APP || this == WEBHOOK;
    }

    /**
     * Check if this channel supports attachments
     */
    public boolean supportsAttachments() {
        return this == EMAIL || this == SLACK || this == TEAMS || this == DISCORD;
    }

    /**
     * Check if this channel supports real-time delivery
     */
    public boolean supportsRealTimeDelivery() {
        return this == PUSH || this == IN_APP || this == WEBHOOK;
    }

    /**
     * Check if this channel has delivery confirmation
     */
    public boolean hasDeliveryConfirmation() {
        return this == EMAIL || this == SMS || this == PUSH;
    }

    /**
     * Check if this channel supports scheduling
     */
    public boolean supportsScheduling() {
        return this != IN_APP; // In-app notifications are typically immediate
    }

    /**
     * Get the priority order for fallback channels
     */
    public int getFallbackPriority() {
        return switch (this) {
            case EMAIL -> 1;
            case SMS -> 2;
            case PUSH -> 3;
            case IN_APP -> 4;
            case WEBHOOK -> 5;
            case SLACK -> 6;
            case TEAMS -> 7;
            case DISCORD -> 8;
        };
    }
}
