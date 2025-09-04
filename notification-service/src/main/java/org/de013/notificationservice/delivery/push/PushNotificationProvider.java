package org.de013.notificationservice.delivery.push;

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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Push notification delivery provider (Mock implementation)
 * In production, this would integrate with Firebase Cloud Messaging (FCM) and Apple Push Notification (APN)
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "notification.push.enabled", havingValue = "true", matchIfMissing = true)
public class PushNotificationProvider implements DeliveryProvider {

    private final PushConfiguration pushConfiguration;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public NotificationChannel getSupportedChannel() {
        return NotificationChannel.PUSH;
    }

    @Override
    public boolean canHandle(Notification notification) {
        return notification.getChannel() == NotificationChannel.PUSH &&
               notification.getRecipientAddress() != null &&
               isValidDeviceToken(notification.getRecipientAddress());
    }

    @Override
    public DeliveryResult deliver(Notification notification) {
        long startTime = System.currentTimeMillis();
        String messageId = UUID.randomUUID().toString();
        
        log.info("Delivering push notification: id={}, recipient={}", 
                notification.getId(), maskDeviceToken(notification.getRecipientAddress()));

        try {
            if (pushConfiguration.isMockMode()) {
                return deliverMockPush(notification, messageId, startTime);
            } else {
                return deliverRealPush(notification, messageId, startTime);
            }
            
        } catch (Exception e) {
            log.error("Unexpected error sending push notification: id={}, error={}", 
                    notification.getId(), e.getMessage(), e);
            return DeliveryResult.failure(DeliveryStatus.PROVIDER_ERROR, 
                    "Unexpected error: " + e.getMessage(), "500", e.getMessage());
        }
    }

    @Override
    public DeliveryResult checkStatus(NotificationDelivery delivery) {
        if (pushConfiguration.isMockMode()) {
            return DeliveryResult.success(delivery.getExternalId(), delivery.getProviderMessageId());
        }
        
        // In production, implement real status check with FCM/APN
        log.debug("Push notification status check not implemented: deliveryId={}", delivery.getId());
        return DeliveryResult.success(delivery.getExternalId(), delivery.getProviderMessageId());
    }

    @Override
    public String getProviderName() {
        return pushConfiguration.isMockMode() ? "MOCK_PUSH" : "FCM";
    }

    @Override
    public boolean isAvailable() {
        if (pushConfiguration.isMockMode()) {
            return true;
        }
        
        return pushConfiguration.getFcmServerKey() != null && 
               !pushConfiguration.getFcmServerKey().isEmpty();
    }

    @Override
    public int getRateLimit() {
        return pushConfiguration.getRateLimit();
    }

    /**
     * Deliver push notification in mock mode
     */
    private DeliveryResult deliverMockPush(Notification notification, String messageId, long startTime) {
        log.info("Mock push delivery: id={}, recipient={}, title={}, body={}", 
                notification.getId(), 
                maskDeviceToken(notification.getRecipientAddress()),
                notification.getSubject(),
                truncateContent(notification.getContent()));
        
        // Simulate processing time
        try {
            Thread.sleep(50 + (long)(Math.random() * 100)); // 50-150ms
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        // Simulate occasional failures (3% failure rate)
        if (Math.random() < 0.03) {
            return DeliveryResult.failure(DeliveryStatus.FAILED, 
                    "Mock push delivery failed", "400", "Invalid device token");
        }
        
        log.info("Mock push sent successfully: id={}, messageId={}, processingTime={}ms", 
                notification.getId(), messageId, processingTime);
        
        return DeliveryResult.success(messageId, messageId, "200", processingTime);
    }

    /**
     * Deliver push notification using FCM (placeholder implementation)
     */
    private DeliveryResult deliverRealPush(Notification notification, String messageId, long startTime) {
        try {
            // FCM payload structure
            Map<String, Object> message = new HashMap<>();
            message.put("token", notification.getRecipientAddress());
            
            Map<String, Object> notificationPayload = new HashMap<>();
            notificationPayload.put("title", notification.getSubject());
            notificationPayload.put("body", notification.getContent());
            
            // Add custom data if available
            Map<String, Object> data = new HashMap<>();
            data.put("notification_id", notification.getId().toString());
            if (notification.getCorrelationId() != null) {
                data.put("correlation_id", notification.getCorrelationId());
            }
            if (notification.getMetadata() != null) {
                notification.getMetadata().forEach((key, value) -> 
                    data.put(key, value.toString()));
            }
            
            message.put("notification", notificationPayload);
            message.put("data", data);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("message", message);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(pushConfiguration.getFcmServerKey());
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            // FCM API endpoint
            String fcmUrl = "https://fcm.googleapis.com/v1/projects/" + 
                           pushConfiguration.getFcmProjectId() + "/messages:send";
            
            ResponseEntity<Map> response = restTemplate.postForEntity(fcmUrl, request, Map.class);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> responseBody = response.getBody();
                String providerMessageId = responseBody != null ? 
                        (String) responseBody.get("name") : messageId;
                
                log.info("Push notification sent successfully: id={}, providerMessageId={}, processingTime={}ms", 
                        notification.getId(), providerMessageId, processingTime);
                
                return DeliveryResult.success(messageId, providerMessageId, 
                        String.valueOf(response.getStatusCode().value()), processingTime);
            } else {
                return DeliveryResult.failure(DeliveryStatus.FAILED, 
                        "FCM returned error", 
                        String.valueOf(response.getStatusCode().value()), 
                        "FCM error");
            }
            
        } catch (Exception e) {
            log.error("Failed to send push notification via FCM: id={}, error={}", 
                    notification.getId(), e.getMessage(), e);
            return DeliveryResult.failure(DeliveryStatus.PROVIDER_ERROR, 
                    "FCM error: " + e.getMessage(), "500", e.getMessage());
        }
    }

    /**
     * Validate device token format
     */
    private boolean isValidDeviceToken(String deviceToken) {
        return deviceToken != null && 
               deviceToken.length() >= 64 && 
               deviceToken.matches("^[a-fA-F0-9:_-]+$");
    }

    /**
     * Mask device token for logging
     */
    private String maskDeviceToken(String deviceToken) {
        if (deviceToken == null || deviceToken.length() < 8) {
            return "****";
        }
        return deviceToken.substring(0, 4) + "****" + deviceToken.substring(deviceToken.length() - 4);
    }

    /**
     * Truncate content for logging
     */
    private String truncateContent(String content) {
        if (content == null) return "";
        return content.length() > 50 ? content.substring(0, 50) + "..." : content;
    }
}
