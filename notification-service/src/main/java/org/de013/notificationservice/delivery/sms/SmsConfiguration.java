package org.de013.notificationservice.delivery.sms;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SMS configuration properties
 */
@Component
@ConfigurationProperties(prefix = "notification.sms")
@Data
public class SmsConfiguration {

    private boolean enabled = true;
    private boolean mockMode = true;
    private String providerName = "TWILIO";
    private String apiUrl = "https://api.twilio.com/2010-04-01/Accounts/{accountSid}";
    private String apiKey;
    private String accountSid;
    private String authToken;
    private String senderNumber;
    private int rateLimit = 50; // messages per minute
    private int maxRetries = 3;
    private int retryDelaySeconds = 120;
    private boolean enableDeliveryReports = true;
    private String webhookUrl;
    private int maxMessageLength = 160;
}
