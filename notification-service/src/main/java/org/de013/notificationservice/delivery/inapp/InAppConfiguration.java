package org.de013.notificationservice.delivery.inapp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * In-App notification configuration properties
 */
@Component
@ConfigurationProperties(prefix = "notification.inapp")
@Data
public class InAppConfiguration {

    private boolean enabled = true;
    private int rateLimit = 500; // messages per minute
    private boolean showBadge = true;
    private boolean autoHide = true;
    private int autoHideDelaySeconds = 5;
    private boolean playSound = true;
    private String soundFile = "notification.mp3";
    private boolean enableActions = true;
    private int maxNotificationsPerUser = 100;
    private int cleanupIntervalHours = 24;
    private boolean persistNotifications = true;
}
