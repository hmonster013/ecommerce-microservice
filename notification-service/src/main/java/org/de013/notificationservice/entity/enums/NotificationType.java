package org.de013.notificationservice.entity.enums;

import lombok.Getter;

/**
 * Notification Type Enumeration
 * Defines different types of notifications that can be sent
 */
@Getter
public enum NotificationType {
    
    // Order related notifications
    ORDER_PLACED("Order Placed", "Notification sent when an order is placed", "order"),
    ORDER_CONFIRMED("Order Confirmed", "Notification sent when an order is confirmed", "order"),
    ORDER_SHIPPED("Order Shipped", "Notification sent when an order is shipped", "order"),
    ORDER_DELIVERED("Order Delivered", "Notification sent when an order is delivered", "order"),
    ORDER_CANCELLED("Order Cancelled", "Notification sent when an order is cancelled", "order"),
    ORDER_RETURNED("Order Returned", "Notification sent when an order is returned", "order"),

    // Additional order notifications
    ORDER_CONFIRMATION("Order Confirmation", "Order confirmation notification", "order"),
    ORDER_UPDATE("Order Update", "Order update notification", "order"),
    
    // Payment related notifications
    PAYMENT_SUCCESS("Payment Success", "Notification sent when payment is successful", "payment"),
    PAYMENT_FAILED("Payment Failed", "Notification sent when payment fails", "payment"),
    PAYMENT_REFUND("Payment Refund", "Notification sent when payment is refunded", "payment"),
    PAYMENT_PENDING("Payment Pending", "Notification sent when payment is pending", "payment"),

    // Additional payment notifications
    PAYMENT_CONFIRMATION("Payment Confirmation", "Payment confirmation notification", "payment"),
    PAYMENT_REFUNDED("Payment Refunded", "Payment refunded notification", "payment"),
    PAYMENT_CANCELLED("Payment Cancelled", "Payment cancelled notification", "payment"),
    
    // User account related notifications
    USER_REGISTRATION("User Registration", "Welcome notification for new users", "account"),
    USER_VERIFICATION("User Verification", "Email verification notification", "account"),
    PASSWORD_RESET("Password Reset", "Password reset notification", "account"),
    ACCOUNT_LOCKED("Account Locked", "Account locked notification", "account"),
    ACCOUNT_UNLOCKED("Account Unlocked", "Account unlocked notification", "account"),

    // Additional user notifications
    WELCOME("Welcome", "Welcome notification for new users", "account"),
    ACCOUNT_ACTIVATION_REMINDER("Account Activation Reminder", "Reminder to activate account", "account"),
    ACCOUNT_ACTIVATED("Account Activated", "Account activation confirmation", "account"),
    GETTING_STARTED("Getting Started", "Getting started guide", "account"),
    PROFILE_UPDATE("Profile Update", "Profile update confirmation", "account"),
    
    // Marketing notifications
    PROMOTIONAL("Promotional", "Marketing and promotional notifications", "marketing"),
    NEWSLETTER("Newsletter", "Newsletter notifications", "marketing"),
    PRODUCT_RECOMMENDATION("Product Recommendation", "Product recommendation notifications", "marketing"),
    SALE_ANNOUNCEMENT("Sale Announcement", "Sale and discount announcements", "marketing"),
    
    // System notifications
    SYSTEM_MAINTENANCE("System Maintenance", "System maintenance notifications", "system"),
    SYSTEM_UPDATE("System Update", "System update notifications", "system"),
    SECURITY_ALERT("Security Alert", "Security related alerts", "system"),
    
    // Inventory notifications
    LOW_STOCK("Low Stock", "Low stock alert notifications", "inventory"),
    OUT_OF_STOCK("Out of Stock", "Out of stock notifications", "inventory"),
    BACK_IN_STOCK("Back in Stock", "Product back in stock notifications", "inventory"),
    
    // General notifications
    REMINDER("Reminder", "General reminder notifications", "general"),
    ANNOUNCEMENT("Announcement", "General announcements", "general"),
    FEEDBACK_REQUEST("Feedback Request", "Request for user feedback", "general"),
    CUSTOM("Custom", "Custom notification type", "custom");

    private final String displayName;
    private final String description;
    private final String category;

    NotificationType(String displayName, String description, String category) {
        this.displayName = displayName;
        this.description = description;
        this.category = category;
    }

    /**
     * Check if this notification type is related to orders
     */
    public boolean isOrderRelated() {
        return "order".equals(category);
    }

    /**
     * Check if this notification type is related to payments
     */
    public boolean isPaymentRelated() {
        return "payment".equals(category);
    }

    /**
     * Check if this notification type is marketing related
     */
    public boolean isMarketingRelated() {
        return "marketing".equals(category);
    }

    /**
     * Check if this notification type is system related
     */
    public boolean isSystemRelated() {
        return "system".equals(category);
    }

    /**
     * Check if this notification type requires immediate delivery
     */
    public boolean requiresImmediateDelivery() {
        return this == PAYMENT_FAILED || 
               this == SECURITY_ALERT || 
               this == ACCOUNT_LOCKED ||
               this == SYSTEM_MAINTENANCE;
    }
}
