package org.de013.notificationservice.delivery.sms;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.delivery.DeliveryProvider;
import org.de013.notificationservice.delivery.DeliveryResult;
import org.de013.notificationservice.entity.Notification;
import org.de013.notificationservice.entity.NotificationDelivery;
import org.de013.notificationservice.entity.enums.DeliveryStatus;
import org.de013.notificationservice.entity.enums.NotificationChannel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.UUID;

/**
 * SMS delivery provider with Twilio integration
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "notification.sms.enabled", havingValue = "true", matchIfMissing = true)
public class SmsDeliveryProvider implements DeliveryProvider {

    private final SmsConfiguration smsConfiguration;

    @PostConstruct
    public void initializeTwilio() {
        if (!smsConfiguration.isMockMode()) {
            if (smsConfiguration.getAccountSid() != null && smsConfiguration.getAuthToken() != null) {
                Twilio.init(smsConfiguration.getAccountSid(), smsConfiguration.getAuthToken());
                log.info("Twilio initialized successfully for account: {}",
                        maskAccountSid(smsConfiguration.getAccountSid()));
            } else {
                log.warn("Twilio credentials not configured. SMS will not work in production mode.");
            }
        }
    }

    @Override
    public NotificationChannel getSupportedChannel() {
        return NotificationChannel.SMS;
    }

    @Override
    public boolean canHandle(Notification notification) {
        return notification.getChannel() == NotificationChannel.SMS &&
               notification.getRecipientAddress() != null &&
               isValidPhoneNumber(notification.getRecipientAddress());
    }

    @Override
    public DeliveryResult deliver(Notification notification) {
        long startTime = System.currentTimeMillis();
        String messageId = UUID.randomUUID().toString();
        
        log.info("Delivering SMS notification: id={}, recipient={}", 
                notification.getId(), maskPhoneNumber(notification.getRecipientAddress()));

        try {
            // Mock SMS delivery - in production, integrate with real SMS provider
            if (smsConfiguration.isMockMode()) {
                return deliverMockSms(notification, messageId, startTime);
            } else {
                return deliverRealSms(notification, messageId, startTime);
            }
            
        } catch (Exception e) {
            log.error("Unexpected error sending SMS: id={}, error={}", 
                    notification.getId(), e.getMessage(), e);
            return DeliveryResult.failure(DeliveryStatus.PROVIDER_ERROR, 
                    "Unexpected error: " + e.getMessage(), "500", e.getMessage());
        }
    }

    @Override
    public DeliveryResult checkStatus(NotificationDelivery delivery) {
        if (smsConfiguration.isMockMode()) {
            // Mock status check - always return success
            return DeliveryResult.success(delivery.getExternalId(), delivery.getProviderMessageId());
        }

        try {
            // Check message status using Twilio SDK
            if (delivery.getProviderMessageId() != null) {
                Message message = Message.fetcher(delivery.getProviderMessageId()).fetch();

                log.debug("Twilio message status: sid={}, status={}, errorCode={}",
                        message.getSid(), message.getStatus(), message.getErrorCode());

                // Map Twilio status to our delivery result
                if (message.getErrorCode() != null) {
                    return DeliveryResult.failure(DeliveryStatus.FAILED,
                            "Twilio error: " + message.getErrorMessage(),
                            String.valueOf(message.getErrorCode()),
                            message.getErrorMessage());
                }

                return DeliveryResult.success(delivery.getExternalId(), message.getSid());
            }

            return DeliveryResult.success(delivery.getExternalId(), delivery.getProviderMessageId());

        } catch (Exception e) {
            log.warn("Failed to check SMS status via Twilio: deliveryId={}, error={}",
                    delivery.getId(), e.getMessage());
            // Return success as fallback - don't fail the whole process
            return DeliveryResult.success(delivery.getExternalId(), delivery.getProviderMessageId());
        }
    }

    @Override
    public String getProviderName() {
        return smsConfiguration.isMockMode() ? "MOCK_SMS" : smsConfiguration.getProviderName();
    }

    @Override
    public boolean isAvailable() {
        if (smsConfiguration.isMockMode()) {
            return true;
        }

        // Check if Twilio credentials are configured
        try {
            return smsConfiguration.getAccountSid() != null &&
                   smsConfiguration.getAuthToken() != null &&
                   smsConfiguration.getSenderNumber() != null &&
                   !smsConfiguration.getAccountSid().isEmpty() &&
                   !smsConfiguration.getAuthToken().isEmpty() &&
                   !smsConfiguration.getSenderNumber().isEmpty();
        } catch (Exception e) {
            log.warn("SMS provider not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public int getRateLimit() {
        return smsConfiguration.getRateLimit();
    }

    /**
     * Deliver SMS in mock mode
     */
    private DeliveryResult deliverMockSms(Notification notification, String messageId, long startTime) {
        log.info("Mock SMS delivery: id={}, recipient={}, content={}", 
                notification.getId(), 
                maskPhoneNumber(notification.getRecipientAddress()),
                truncateContent(notification.getContent()));
        
        // Simulate processing time
        try {
            Thread.sleep(100 + (long)(Math.random() * 200)); // 100-300ms
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        // Simulate occasional failures (5% failure rate)
        if (Math.random() < 0.05) {
            return DeliveryResult.failure(DeliveryStatus.FAILED, 
                    "Mock SMS delivery failed", "400", "Simulated failure");
        }
        
        log.info("Mock SMS sent successfully: id={}, messageId={}, processingTime={}ms", 
                notification.getId(), messageId, processingTime);
        
        return DeliveryResult.success(messageId, messageId, "200", processingTime);
    }

    /**
     * Deliver SMS using Twilio SDK
     */
    private DeliveryResult deliverRealSms(Notification notification, String messageId, long startTime) {
        try {
            // Create Twilio message
            Message message = Message.creator(
                    new PhoneNumber(notification.getRecipientAddress()), // To
                    new PhoneNumber(smsConfiguration.getSenderNumber()),  // From
                    truncateForSms(notification.getContent())             // Body
            ).create();

            long processingTime = System.currentTimeMillis() - startTime;

            log.info("SMS sent successfully via Twilio: id={}, twilioSid={}, status={}, processingTime={}ms",
                    notification.getId(), message.getSid(), message.getStatus(), processingTime);

            // Check if message was accepted by Twilio
            if (message.getErrorCode() != null) {
                log.error("Twilio returned error: code={}, message={}",
                        message.getErrorCode(), message.getErrorMessage());
                return DeliveryResult.failure(DeliveryStatus.FAILED,
                        "Twilio error: " + message.getErrorMessage(),
                        String.valueOf(message.getErrorCode()),
                        message.getErrorMessage());
            }

            return DeliveryResult.success(messageId, message.getSid(), "200", processingTime);

        } catch (Exception e) {
            log.error("Failed to send SMS via Twilio: id={}, error={}",
                    notification.getId(), e.getMessage(), e);
            return DeliveryResult.failure(DeliveryStatus.PROVIDER_ERROR,
                    "Twilio error: " + e.getMessage(), "500", e.getMessage());
        }
    }

    /**
     * Validate phone number format
     */
    private boolean isValidPhoneNumber(String phoneNumber) {
        return phoneNumber != null && phoneNumber.matches("^\\+?[1-9]\\d{1,14}$");
    }

    /**
     * Mask phone number for logging
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }
        return phoneNumber.substring(0, 2) + "****" + phoneNumber.substring(phoneNumber.length() - 2);
    }

    /**
     * Truncate content for logging
     */
    private String truncateContent(String content) {
        if (content == null) return "";
        return content.length() > 50 ? content.substring(0, 50) + "..." : content;
    }

    /**
     * Truncate content for SMS (160 characters limit)
     */
    private String truncateForSms(String content) {
        if (content == null) return "";
        return content.length() > smsConfiguration.getMaxMessageLength() ?
                content.substring(0, smsConfiguration.getMaxMessageLength() - 3) + "..." : content;
    }

    /**
     * Mask account SID for logging
     */
    private String maskAccountSid(String accountSid) {
        if (accountSid == null || accountSid.length() < 8) {
            return "****";
        }
        return accountSid.substring(0, 4) + "****" + accountSid.substring(accountSid.length() - 4);
    }
}
