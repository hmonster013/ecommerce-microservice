package org.de013.notificationservice.delivery.email;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Email configuration properties
 */
@Component
@ConfigurationProperties(prefix = "notification.email")
@Data
public class EmailConfiguration {

    private boolean enabled = true;
    private String defaultSender = "noreply@company.com";
    private String defaultSenderName = "Company Name";
    private String replyTo;
    private int rateLimit = 100; // messages per minute
    private int maxRetries = 3;
    private int retryDelaySeconds = 60;
    private boolean trackOpens = false;
    private boolean trackClicks = false;
    private String unsubscribeUrl;
    private String trackingDomain;
}
