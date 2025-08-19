package org.de013.orderservice.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import org.de013.orderservice.entity.enums.TrackingStatus;

import java.time.LocalDateTime;

/**
 * Add Tracking Request DTO
 * 
 * Request object for adding tracking information to an order.
 * Contains tracking status updates and location information.
 * 
 * @author Development Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddTrackingRequest {
    
    /**
     * ID of the order to add tracking for
     */
    @NotNull(message = "Order ID is required")
    @Positive(message = "Order ID must be positive")
    private Long orderId;
    
    /**
     * Tracking status
     */
    @NotNull(message = "Tracking status is required")
    private TrackingStatus trackingStatus;
    
    /**
     * Location where the status update occurred
     */
    @Size(max = 500, message = "Location must not exceed 500 characters")
    private String location;
    
    /**
     * Detailed location information
     */
    @Size(max = 1000, message = "Location details must not exceed 1000 characters")
    private String locationDetails;
    
    /**
     * City where the status update occurred
     */
    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;
    
    /**
     * State/Province where the status update occurred
     */
    @Size(max = 100, message = "State must not exceed 100 characters")
    private String state;
    
    /**
     * Country where the status update occurred
     */
    @Size(min = 2, max = 3, message = "Country code must be 2-3 characters")
    @Pattern(regexp = "^[A-Z]{2,3}$", message = "Country code must be uppercase letters")
    private String country;
    
    /**
     * Postal code where the status update occurred
     */
    @Size(max = 20, message = "Postal code must not exceed 20 characters")
    private String postalCode;
    
    /**
     * Timestamp when the status update occurred
     */
    private LocalDateTime timestamp;
    
    /**
     * Additional notes for this tracking update
     */
    @Size(max = 2000, message = "Notes must not exceed 2000 characters")
    private String notes;
    
    /**
     * Tracking number from shipping carrier
     */
    @Size(max = 100, message = "Tracking number must not exceed 100 characters")
    private String trackingNumber;
    
    /**
     * Shipping carrier name
     */
    @Size(max = 100, message = "Carrier must not exceed 100 characters")
    private String carrier;
    
    /**
     * Carrier service type
     */
    @Size(max = 100, message = "Carrier service must not exceed 100 characters")
    private String carrierService;
    
    /**
     * Estimated delivery date provided by carrier
     */
    private LocalDateTime estimatedDeliveryDate;
    
    /**
     * Actual delivery date (if delivered)
     */
    private LocalDateTime actualDeliveryDate;
    
    /**
     * Delivery attempt number (for failed delivery attempts)
     */
    @Min(value = 0, message = "Delivery attempt must be non-negative")
    private Integer deliveryAttempt;
    
    /**
     * Reason for failed delivery (if applicable)
     */
    @Size(max = 500, message = "Delivery failure reason must not exceed 500 characters")
    private String deliveryFailureReason;
    
    /**
     * Name of person who received the package (if delivered)
     */
    @Size(max = 200, message = "Received by must not exceed 200 characters")
    private String receivedBy;
    
    /**
     * Whether signature was required for delivery
     */
    @Builder.Default
    private Boolean signatureRequired = false;
    
    /**
     * Whether signature was obtained (if required)
     */
    @Builder.Default
    private Boolean signatureObtained = false;
    
    /**
     * Photo proof of delivery URL
     */
    @Size(max = 1000, message = "Proof of delivery URL must not exceed 1000 characters")
    private String proofOfDeliveryUrl;
    
    /**
     * Whether this is an automated update from carrier
     */
    @Builder.Default
    private Boolean isAutomated = false;
    
    /**
     * Source of the tracking update
     */
    @Size(max = 20, message = "Update source must not exceed 20 characters")
    @Builder.Default
    private String updateSource = "MANUAL";
    
    /**
     * User ID who created this tracking update (if manual)
     */
    @Positive(message = "Updated by user ID must be positive")
    private Long updatedByUserId;
    
    /**
     * External tracking ID from carrier system
     */
    @Size(max = 200, message = "External tracking ID must not exceed 200 characters")
    private String externalTrackingId;
    
    /**
     * Additional metadata in JSON format
     */
    @Size(max = 2000, message = "Metadata must not exceed 2000 characters")
    private String metadata;
    
    /**
     * Whether this tracking update is visible to customer
     */
    @Builder.Default
    private Boolean isCustomerVisible = true;
    
    /**
     * Priority level of this tracking update
     */
    @Min(value = 1, message = "Priority level must be at least 1")
    @Max(value = 5, message = "Priority level must be at most 5")
    @Builder.Default
    private Integer priorityLevel = 3;
    
    /**
     * Whether to send notification for this update
     */
    @Builder.Default
    private Boolean sendNotification = true;
    
    /**
     * Notification preferences
     */
    private NotificationPreferencesDto notificationPreferences;
    
    /**
     * Notification Preferences DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NotificationPreferencesDto {
        
        /**
         * Whether to send email notification
         */
        @Builder.Default
        private Boolean sendEmail = true;
        
        /**
         * Whether to send SMS notification
         */
        @Builder.Default
        private Boolean sendSms = false;
        
        /**
         * Whether to send push notification
         */
        @Builder.Default
        private Boolean sendPush = true;
        
        /**
         * Custom email template to use
         */
        @Size(max = 100, message = "Email template must not exceed 100 characters")
        private String emailTemplate;
        
        /**
         * Custom SMS template to use
         */
        @Size(max = 100, message = "SMS template must not exceed 100 characters")
        private String smsTemplate;
        
        /**
         * Additional notification data
         */
        private String notificationData;
    }
    
    /**
     * Get effective timestamp (current time if not provided)
     */
    public LocalDateTime getEffectiveTimestamp() {
        return timestamp != null ? timestamp : LocalDateTime.now();
    }
    
    /**
     * Get complete location string
     */
    public String getCompleteLocation() {
        StringBuilder locationBuilder = new StringBuilder();
        
        if (location != null && !location.trim().isEmpty()) {
            locationBuilder.append(location.trim());
        }
        
        if (city != null && !city.trim().isEmpty()) {
            if (locationBuilder.length() > 0) {
                locationBuilder.append(", ");
            }
            locationBuilder.append(city.trim());
        }
        
        if (state != null && !state.trim().isEmpty()) {
            if (locationBuilder.length() > 0) {
                locationBuilder.append(", ");
            }
            locationBuilder.append(state.trim());
        }
        
        if (country != null && !country.trim().isEmpty()) {
            if (locationBuilder.length() > 0) {
                locationBuilder.append(", ");
            }
            locationBuilder.append(country.trim());
        }
        
        return locationBuilder.toString();
    }
    
    /**
     * Check if this is a delivery update
     */
    public boolean isDeliveryUpdate() {
        return trackingStatus != null && trackingStatus.isDelivered();
    }
    
    /**
     * Check if this is a problem update
     */
    public boolean isProblemUpdate() {
        return trackingStatus != null && trackingStatus.isProblem();
    }
    
    /**
     * Check if this update requires customer action
     */
    public boolean requiresCustomerAction() {
        return trackingStatus != null && trackingStatus.requiresCustomerAction();
    }
    
    /**
     * Check if this is a delivery attempt
     */
    public boolean isDeliveryAttempt() {
        return deliveryAttempt != null && deliveryAttempt > 0;
    }
    
    /**
     * Check if delivery was successful
     */
    public boolean isDeliverySuccessful() {
        return isDeliveryUpdate() && actualDeliveryDate != null;
    }
    
    /**
     * Check if delivery failed
     */
    public boolean isDeliveryFailed() {
        return isDeliveryAttempt() && !isDeliverySuccessful() && 
               deliveryFailureReason != null && !deliveryFailureReason.trim().isEmpty();
    }
    
    /**
     * Check if signature validation is complete
     */
    public boolean isSignatureValidationComplete() {
        if (!Boolean.TRUE.equals(signatureRequired)) {
            return true; // No signature required
        }
        return Boolean.TRUE.equals(signatureObtained);
    }
    
    /**
     * Check if proof of delivery is provided
     */
    public boolean hasProofOfDelivery() {
        return proofOfDeliveryUrl != null && !proofOfDeliveryUrl.trim().isEmpty();
    }
    
    /**
     * Check if this update should trigger notifications
     */
    public boolean shouldSendNotification() {
        return Boolean.TRUE.equals(sendNotification) && 
               Boolean.TRUE.equals(isCustomerVisible);
    }
    
    /**
     * Get notification channels to use
     */
    public java.util.List<String> getNotificationChannels() {
        java.util.List<String> channels = new java.util.ArrayList<>();
        
        if (notificationPreferences != null) {
            if (Boolean.TRUE.equals(notificationPreferences.getSendEmail())) {
                channels.add("EMAIL");
            }
            if (Boolean.TRUE.equals(notificationPreferences.getSendSms())) {
                channels.add("SMS");
            }
            if (Boolean.TRUE.equals(notificationPreferences.getSendPush())) {
                channels.add("PUSH");
            }
        } else {
            // Default notification channels
            channels.add("EMAIL");
            channels.add("PUSH");
        }
        
        return channels;
    }
    
    /**
     * Validate tracking request
     */
    public boolean isValid() {
        // If delivery status, actual delivery date should be provided
        if (isDeliveryUpdate() && actualDeliveryDate == null) {
            return false;
        }
        
        // If delivery failed, failure reason should be provided
        if (isDeliveryFailed() && (deliveryFailureReason == null || deliveryFailureReason.trim().isEmpty())) {
            return false;
        }
        
        // If signature required but not obtained, should not be marked as delivered
        if (isDeliveryUpdate() && Boolean.TRUE.equals(signatureRequired) && !Boolean.TRUE.equals(signatureObtained)) {
            return false;
        }
        
        return true;
    }
}
