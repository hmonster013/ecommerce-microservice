package org.de013.notificationservice.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.notificationservice.service.DeliveryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for processing notification delivery queues
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "notification.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class DeliveryScheduler {

    private final DeliveryService deliveryService;

    /**
     * Process delivery queue every 30 seconds
     */
    @Scheduled(fixedDelay = 30000) // 30 seconds
    public void processDeliveryQueue() {
        try {
            log.debug("Starting scheduled delivery queue processing");
            deliveryService.processDeliveryQueue();
            log.debug("Completed scheduled delivery queue processing");
        } catch (Exception e) {
            log.error("Error in scheduled delivery queue processing: {}", e.getMessage(), e);
        }
    }

    /**
     * Process retry queue every 2 minutes
     */
    @Scheduled(fixedDelay = 120000) // 2 minutes
    public void processRetryQueue() {
        try {
            log.debug("Starting scheduled retry queue processing");
            deliveryService.processRetryQueue();
            log.debug("Completed scheduled retry queue processing");
        } catch (Exception e) {
            log.error("Error in scheduled retry queue processing: {}", e.getMessage(), e);
        }
    }

    /**
     * Check delivery statuses every 5 minutes
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes
    public void checkDeliveryStatuses() {
        try {
            log.debug("Starting scheduled delivery status check");
            deliveryService.checkDeliveryStatuses();
            log.debug("Completed scheduled delivery status check");
        } catch (Exception e) {
            log.error("Error in scheduled delivery status check: {}", e.getMessage(), e);
        }
    }
}
