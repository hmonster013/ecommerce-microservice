package org.de013.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.delivery.DeliveryProvider;
import org.de013.notificationservice.delivery.DeliveryResult;
import org.de013.notificationservice.entity.Notification;
import org.de013.notificationservice.entity.NotificationDelivery;
import org.de013.notificationservice.entity.enums.DeliveryStatus;
import org.de013.notificationservice.entity.enums.NotificationStatus;
import org.de013.notificationservice.repository.NotificationDeliveryRepository;
import org.de013.notificationservice.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing notification delivery across multiple channels
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DeliveryService {

    private final List<DeliveryProvider> deliveryProviders;
    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final RateLimitingService rateLimitingService;
    private final DeliveryAnalyticsService analyticsService;

    /**
     * Deliver a notification using appropriate provider
     */
    @Transactional
    public void deliverNotification(Notification notification) {
        log.info("Starting delivery for notification: id={}, channel={}, type={}", 
                notification.getId(), notification.getChannel(), notification.getType());

        // Find appropriate delivery provider
        Optional<DeliveryProvider> provider = findProvider(notification);
        if (provider.isEmpty()) {
            log.error("No delivery provider found for notification: id={}, channel={}", 
                    notification.getId(), notification.getChannel());
            markNotificationAsFailed(notification, "No delivery provider available for channel: " + notification.getChannel());
            return;
        }

        DeliveryProvider deliveryProvider = provider.get();
        
        // Check if provider is available
        if (!deliveryProvider.isAvailable()) {
            log.warn("Delivery provider not available: provider={}, notification={}",
                    deliveryProvider.getProviderName(), notification.getId());
            markNotificationAsFailed(notification, "Delivery provider not available: " + deliveryProvider.getProviderName());
            return;
        }

        // Check rate limits
        if (!rateLimitingService.isUserWithinRateLimit(notification.getUserId(), notification.getChannel(), notification.getType())) {
            log.warn("User rate limit exceeded: userId={}, channel={}, type={}",
                    notification.getUserId(), notification.getChannel(), notification.getType());
            markNotificationAsFailed(notification, "User rate limit exceeded");
            return;
        }

        if (!rateLimitingService.isProviderWithinRateLimit(notification.getChannel())) {
            log.warn("Provider rate limit exceeded: channel={}", notification.getChannel());
            markNotificationAsFailed(notification, "Provider rate limit exceeded");
            return;
        }

        if (rateLimitingService.isBurstProtectionTriggered(notification.getUserId(), notification.getChannel())) {
            log.warn("Burst protection triggered: userId={}, channel={}",
                    notification.getUserId(), notification.getChannel());
            markNotificationAsFailed(notification, "Burst protection triggered");
            return;
        }

        // Create delivery record
        NotificationDelivery delivery = createDeliveryRecord(notification, deliveryProvider);
        delivery = deliveryRepository.save(delivery);

        // Update notification status
        notification.setStatus(NotificationStatus.PROCESSING);
        notificationRepository.save(notification);

        try {
            // Record rate limiting attempt
            rateLimitingService.recordNotificationAttempt(notification.getUserId(), notification.getChannel(), notification.getType());

            // Attempt delivery
            long startTime = System.currentTimeMillis();
            DeliveryResult result = deliveryProvider.deliver(notification);
            long processingTime = System.currentTimeMillis() - startTime;

            // Record analytics
            analyticsService.recordDeliveryAttempt(notification.getId(), notification.getChannel(),
                    notification.getType(), result.getStatus(), processingTime);

            // Update delivery record with result
            updateDeliveryRecord(delivery, result);

            // Update notification based on result
            if (result.isSuccess()) {
                handleSuccessfulDelivery(notification, result);
            } else {
                handleFailedDelivery(notification, delivery, result);
            }
            
        } catch (Exception e) {
            log.error("Unexpected error during delivery: notification={}, provider={}, error={}", 
                    notification.getId(), deliveryProvider.getProviderName(), e.getMessage(), e);
            
            // Mark delivery as failed
            delivery.markAsFailed("Unexpected error: " + e.getMessage(), "500");
            deliveryRepository.save(delivery);
            
            // Handle failed notification
            markNotificationAsFailed(notification, "Delivery failed with unexpected error: " + e.getMessage());
        }
    }

    /**
     * Process delivery queue - find and deliver pending notifications
     */
    @Transactional
    public void processDeliveryQueue() {
        log.debug("Processing delivery queue");
        
        List<NotificationStatus> readyStatuses = List.of(NotificationStatus.PENDING, NotificationStatus.QUEUED);
        List<Notification> pendingNotifications = notificationRepository.findReadyForDelivery(readyStatuses, LocalDateTime.now());
        log.info("Found {} notifications ready for delivery", pendingNotifications.size());
        
        for (Notification notification : pendingNotifications) {
            try {
                deliverNotification(notification);
            } catch (Exception e) {
                log.error("Error processing notification in queue: id={}, error={}", 
                        notification.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Process retry queue - find and retry failed notifications
     */
    @Transactional
    public void processRetryQueue() {
        log.debug("Processing retry queue");
        
        List<Notification> retryNotifications = notificationRepository.findReadyForRetry(NotificationStatus.RETRY, LocalDateTime.now());
        log.info("Found {} notifications ready for retry", retryNotifications.size());
        
        for (Notification notification : retryNotifications) {
            try {
                deliverNotification(notification);
            } catch (Exception e) {
                log.error("Error retrying notification: id={}, error={}", 
                        notification.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Check delivery status for pending deliveries
     */
    @Transactional
    public void checkDeliveryStatuses() {
        log.debug("Checking delivery statuses");
        
        List<DeliveryStatus> pendingStatuses = List.of(DeliveryStatus.PENDING, DeliveryStatus.IN_PROGRESS);
        List<NotificationDelivery> pendingDeliveries = deliveryRepository.findReadyForRetry(pendingStatuses, LocalDateTime.now());
        
        log.info("Found {} deliveries to check status", pendingDeliveries.size());
        
        for (NotificationDelivery delivery : pendingDeliveries) {
            try {
                checkDeliveryStatus(delivery);
            } catch (Exception e) {
                log.error("Error checking delivery status: deliveryId={}, error={}", 
                        delivery.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Find appropriate delivery provider for notification
     */
    private Optional<DeliveryProvider> findProvider(Notification notification) {
        return deliveryProviders.stream()
                .filter(provider -> provider.canHandle(notification))
                .findFirst();
    }

    /**
     * Create delivery record
     */
    private NotificationDelivery createDeliveryRecord(Notification notification, DeliveryProvider provider) {
        return NotificationDelivery.builder()
                .notification(notification)
                .channel(notification.getChannel())
                .status(DeliveryStatus.PENDING)
                .recipientAddress(notification.getRecipientAddress())
                .senderAddress(notification.getSenderAddress())
                .providerName(provider.getProviderName())
                .maxAttempts(notification.getMaxRetryAttempts())
                .build();
    }

    /**
     * Update delivery record with result
     */
    private void updateDeliveryRecord(NotificationDelivery delivery, DeliveryResult result) {
        delivery.setStatus(result.getStatus());
        delivery.setExternalId(result.getExternalId());
        delivery.setProviderMessageId(result.getProviderMessageId());
        delivery.setResponseCode(result.getResponseCode());
        delivery.setResponseMessage(result.getResponseMessage());
        delivery.setErrorMessage(result.getErrorMessage());
        delivery.setProcessingTimeMs(result.getProcessingTimeMs());
        delivery.setCostCents(result.getCostCents());
        delivery.setProviderResponse(result.getProviderResponse());
        delivery.setDeliveryMetadata(result.getMetadata());
        
        if (result.isSuccess()) {
            delivery.setDeliveredAt(result.getDeliveredAt() != null ? result.getDeliveredAt() : LocalDateTime.now());
        } else {
            delivery.setFailedAt(LocalDateTime.now());
        }
        
        delivery.incrementAttempt(0); // Mark as attempted
        deliveryRepository.save(delivery);
    }

    /**
     * Handle successful delivery
     */
    private void handleSuccessfulDelivery(Notification notification, DeliveryResult result) {
        if (result.getStatus() == DeliveryStatus.SUCCESS) {
            notification.markAsDelivered();
            notification.setExternalId(result.getExternalId());
        } else {
            notification.markAsSent();
            notification.setExternalId(result.getExternalId());
        }
        
        notificationRepository.save(notification);
        
        log.info("Notification delivered successfully: id={}, status={}, externalId={}", 
                notification.getId(), result.getStatus(), result.getExternalId());
    }

    /**
     * Handle failed delivery
     */
    private void handleFailedDelivery(Notification notification, NotificationDelivery delivery, DeliveryResult result) {
        log.warn("Notification delivery failed: id={}, status={}, error={}", 
                notification.getId(), result.getStatus(), result.getErrorMessage());
        
        // Check if retry is possible
        if (notification.canRetry() && delivery.canRetry()) {
            int delaySeconds = result.getStatus().getRetryDelaySeconds();
            notification.incrementRetry(delaySeconds);
            delivery.setNextAttemptAt(LocalDateTime.now().plusSeconds(delaySeconds));
            
            log.info("Notification scheduled for retry: id={}, attempt={}, nextRetry={}", 
                    notification.getId(), notification.getRetryCount(), notification.getNextRetryAt());
        } else {
            // Mark as permanently failed
            notification.markAsFailed(result.getErrorMessage());
            log.warn("Notification permanently failed: id={}, maxRetries={}, currentRetries={}", 
                    notification.getId(), notification.getMaxRetryAttempts(), notification.getRetryCount());
        }
        
        notificationRepository.save(notification);
    }

    /**
     * Mark notification as failed
     */
    private void markNotificationAsFailed(Notification notification, String errorMessage) {
        notification.markAsFailed(errorMessage);
        notificationRepository.save(notification);
        
        log.error("Notification marked as failed: id={}, error={}", notification.getId(), errorMessage);
    }

    /**
     * Check delivery status for a specific delivery
     */
    private void checkDeliveryStatus(NotificationDelivery delivery) {
        Optional<DeliveryProvider> provider = deliveryProviders.stream()
                .filter(p -> p.getProviderName().equals(delivery.getProviderName()))
                .findFirst();
        
        if (provider.isEmpty()) {
            log.warn("Provider not found for delivery status check: deliveryId={}, provider={}", 
                    delivery.getId(), delivery.getProviderName());
            return;
        }
        
        try {
            DeliveryResult result = provider.get().checkStatus(delivery);
            updateDeliveryRecord(delivery, result);
            
            // Update notification if status changed
            if (result.getStatus() == DeliveryStatus.SUCCESS && delivery.getNotification().getStatus() != NotificationStatus.DELIVERED) {
                delivery.getNotification().markAsDelivered();
                notificationRepository.save(delivery.getNotification());
            }
            
        } catch (Exception e) {
            log.error("Error checking delivery status: deliveryId={}, error={}", 
                    delivery.getId(), e.getMessage(), e);
        }
    }
}
