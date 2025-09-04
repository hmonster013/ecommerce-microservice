package org.de013.notificationservice.delivery.push;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Push notification configuration properties
 */
@Component
@ConfigurationProperties(prefix = "notification.push")
@Data
public class PushConfiguration {

    private boolean enabled = true;
    private boolean mockMode = true;
    
    // Firebase Cloud Messaging (FCM) configuration
    private String fcmServerKey;
    private String fcmProjectId;
    private String fcmServiceAccountPath;
    
    // Apple Push Notification (APN) configuration
    private String apnKeyId;
    private String apnTeamId;
    private String apnKeyPath;
    private boolean apnProduction = false;
    
    // General configuration
    private int rateLimit = 200; // messages per minute
    private int maxRetries = 3;
    private int retryDelaySeconds = 60;
    private int timeToLive = 86400; // 24 hours in seconds
    private String defaultSound = "default";
    private int defaultBadge = 1;
}
