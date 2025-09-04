package org.de013.notificationservice.delivery.sms;

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
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SMS delivery provider (Mock implementation)
 * In production, this would integrate with Twilio, AWS SNS, or other SMS providers
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "notification.sms.enabled", havingValue = "true", matchIfMissing = true)
public class SmsDeliveryProvider implements DeliveryProvider {

    private final SmsConfiguration smsConfiguration;
    private final RestTemplate restTemplate = new RestTemplate();

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
        
        // In production, implement real status check with SMS provider API
        log.debug("SMS status check not implemented for real provider: deliveryId={}", delivery.getId());
        return DeliveryResult.success(delivery.getExternalId(), delivery.getProviderMessageId());
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
        
        // In production, check provider API availability
        try {
            // Mock availability check
            return smsConfiguration.getApiKey() != null && !smsConfiguration.getApiKey().isEmpty();
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
     * Deliver SMS using real provider (placeholder implementation)
     */
    private DeliveryResult deliverRealSms(Notification notification, String messageId, long startTime) {
        // This is a placeholder for real SMS provider integration
        // Example for Twilio integration:
        
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("to", notification.getRecipientAddress());
            requestBody.put("from", smsConfiguration.getSenderNumber());
            requestBody.put("body", truncateForSms(notification.getContent()));
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(smsConfiguration.getApiKey());
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            // Mock API call - replace with real provider endpoint
            String apiUrl = smsConfiguration.getApiUrl() + "/messages";
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, request, Map.class);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> responseBody = response.getBody();
                String providerMessageId = responseBody != null ? 
                        (String) responseBody.get("sid") : messageId;
                
                log.info("SMS sent successfully: id={}, providerMessageId={}, processingTime={}ms", 
                        notification.getId(), providerMessageId, processingTime);
                
                return DeliveryResult.success(messageId, providerMessageId, 
                        String.valueOf(response.getStatusCode().value()), processingTime);
            } else {
                return DeliveryResult.failure(DeliveryStatus.FAILED, 
                        "SMS provider returned error", 
                        String.valueOf(response.getStatusCode().value()), 
                        "Provider error");
            }
            
        } catch (Exception e) {
            log.error("Failed to send SMS via provider: id={}, error={}", 
                    notification.getId(), e.getMessage(), e);
            return DeliveryResult.failure(DeliveryStatus.PROVIDER_ERROR, 
                    "Provider error: " + e.getMessage(), "500", e.getMessage());
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
        return content.length() > 160 ? content.substring(0, 157) + "..." : content;
    }
}
