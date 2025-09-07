package org.de013.notificationservice.entity.enums;

/**
 * Simple Notification Status Enumeration
 */
public enum NotificationStatus {
    PENDING,    // Notification is pending delivery
    SENT,       // Notification has been sent
    READ,       // Notification has been read by recipient
    FAILED      // Notification delivery failed
}
