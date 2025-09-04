package org.de013.notificationservice.delivery;

import org.de013.notificationservice.entity.Notification;
import org.de013.notificationservice.entity.NotificationDelivery;
import org.de013.notificationservice.entity.enums.NotificationChannel;

/**
 * Interface for notification delivery providers
 */
public interface DeliveryProvider {

    /**
     * Get the channel this provider supports
     */
    NotificationChannel getSupportedChannel();

    /**
     * Check if this provider can handle the notification
     */
    boolean canHandle(Notification notification);

    /**
     * Deliver the notification
     */
    DeliveryResult deliver(Notification notification);

    /**
     * Check delivery status from external provider
     */
    DeliveryResult checkStatus(NotificationDelivery delivery);

    /**
     * Get provider name
     */
    String getProviderName();

    /**
     * Check if provider is available
     */
    boolean isAvailable();

    /**
     * Get rate limit per minute
     */
    int getRateLimit();
}
