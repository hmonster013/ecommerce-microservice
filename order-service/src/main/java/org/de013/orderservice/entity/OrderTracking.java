package org.de013.orderservice.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.de013.common.entity.BaseEntity;
import org.de013.orderservice.entity.enums.TrackingStatus;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Order Tracking Entity
 * 
 * Represents tracking information and status updates for an order.
 * Maintains a complete audit trail of order progress.
 * 
 * @author Development Team
 * @version 1.0.0
 */
@Entity
@Table(name = "order_tracking", indexes = {
    @Index(name = "idx_order_id", columnList = "order_id"),
    @Index(name = "idx_tracking_status", columnList = "tracking_status"),
    @Index(name = "idx_order_status", columnList = "order_id, tracking_status"),
    @Index(name = "idx_timestamp", columnList = "timestamp"),
    @Index(name = "idx_tracking_number", columnList = "tracking_number"),
    @Index(name = "idx_carrier", columnList = "carrier")
})
@SQLDelete(sql = "UPDATE order_tracking SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"order"})
@EqualsAndHashCode(callSuper = true, exclude = {"order"})
public class OrderTracking extends BaseEntity {
    
    /**
     * Reference to the parent order
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @NotNull(message = "Order is required")
    private Order order;
    
    /**
     * Tracking status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tracking_status", length = 30, nullable = false)
    @NotNull(message = "Tracking status is required")
    private TrackingStatus trackingStatus;
    
    /**
     * Location where the status update occurred
     */
    @Column(name = "location", length = 500)
    @Size(max = 500, message = "Location must not exceed 500 characters")
    private String location;
    
    /**
     * Detailed location information (address, facility name, etc.)
     */
    @Column(name = "location_details", length = 1000)
    @Size(max = 1000, message = "Location details must not exceed 1000 characters")
    private String locationDetails;
    
    /**
     * City where the status update occurred
     */
    @Column(name = "city", length = 100)
    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;
    
    /**
     * State/Province where the status update occurred
     */
    @Column(name = "state", length = 100)
    @Size(max = 100, message = "State must not exceed 100 characters")
    private String state;
    
    /**
     * Country where the status update occurred
     */
    @Column(name = "country", length = 3)
    @Size(min = 2, max = 3, message = "Country code must be 2-3 characters")
    @Pattern(regexp = "^[A-Z]{2,3}$", message = "Country code must be uppercase letters")
    private String country;
    
    /**
     * Postal code where the status update occurred
     */
    @Column(name = "postal_code", length = 20)
    @Size(max = 20, message = "Postal code must not exceed 20 characters")
    private String postalCode;
    
    /**
     * Timestamp when the status update occurred
     */
    @Column(name = "timestamp", nullable = false)
    @NotNull(message = "Timestamp is required")
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    /**
     * Additional notes or description for this tracking update
     */
    @Column(name = "notes", length = 2000)
    @Size(max = 2000, message = "Notes must not exceed 2000 characters")
    private String notes;
    
    /**
     * Tracking number from shipping carrier
     */
    @Column(name = "tracking_number", length = 100)
    @Size(max = 100, message = "Tracking number must not exceed 100 characters")
    private String trackingNumber;
    
    /**
     * Shipping carrier name
     */
    @Column(name = "carrier", length = 100)
    @Size(max = 100, message = "Carrier must not exceed 100 characters")
    private String carrier;
    
    /**
     * Carrier service type (standard, express, overnight, etc.)
     */
    @Column(name = "carrier_service", length = 100)
    @Size(max = 100, message = "Carrier service must not exceed 100 characters")
    private String carrierService;
    
    /**
     * Estimated delivery date provided by carrier
     */
    @Column(name = "estimated_delivery_date")
    private LocalDateTime estimatedDeliveryDate;
    
    /**
     * Actual delivery date (if delivered)
     */
    @Column(name = "actual_delivery_date")
    private LocalDateTime actualDeliveryDate;
    
    /**
     * Delivery attempt number (for failed delivery attempts)
     */
    @Column(name = "delivery_attempt")
    @Min(value = 0, message = "Delivery attempt must be non-negative")
    private Integer deliveryAttempt;
    
    /**
     * Reason for failed delivery (if applicable)
     */
    @Column(name = "delivery_failure_reason", length = 500)
    @Size(max = 500, message = "Delivery failure reason must not exceed 500 characters")
    private String deliveryFailureReason;
    
    /**
     * Name of person who received the package (if delivered)
     */
    @Column(name = "received_by", length = 200)
    @Size(max = 200, message = "Received by must not exceed 200 characters")
    private String receivedBy;
    
    /**
     * Signature required for delivery
     */
    @Column(name = "signature_required")
    @Builder.Default
    private Boolean signatureRequired = false;
    
    /**
     * Whether signature was obtained (if required)
     */
    @Column(name = "signature_obtained")
    @Builder.Default
    private Boolean signatureObtained = false;
    
    /**
     * Photo proof of delivery URL
     */
    @Column(name = "proof_of_delivery_url", length = 1000)
    @Size(max = 1000, message = "Proof of delivery URL must not exceed 1000 characters")
    private String proofOfDeliveryUrl;
    
    /**
     * Whether this is an automated update from carrier
     */
    @Column(name = "is_automated")
    @Builder.Default
    private Boolean isAutomated = false;
    
    /**
     * Source of the tracking update (CARRIER, MANUAL, SYSTEM, etc.)
     */
    @Column(name = "update_source", length = 20)
    @Size(max = 20, message = "Update source must not exceed 20 characters")
    @Builder.Default
    private String updateSource = "SYSTEM";
    
    /**
     * User ID who created this tracking update (if manual)
     */
    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;
    
    /**
     * External tracking ID from carrier system
     */
    @Column(name = "external_tracking_id", length = 200)
    @Size(max = 200, message = "External tracking ID must not exceed 200 characters")
    private String externalTrackingId;
    
    /**
     * Additional metadata in JSON format
     */
    @Column(name = "metadata", length = 2000)
    @Size(max = 2000, message = "Metadata must not exceed 2000 characters")
    private String metadata;
    
    /**
     * Whether this tracking update is visible to customer
     */
    @Column(name = "is_customer_visible")
    @Builder.Default
    private Boolean isCustomerVisible = true;
    
    /**
     * Priority level of this tracking update
     */
    @Column(name = "priority_level")
    @Min(value = 1, message = "Priority level must be at least 1")
    @Max(value = 5, message = "Priority level must be at most 5")
    @Builder.Default
    private Integer priorityLevel = 3;
    
    /**
     * Soft delete timestamp
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    
    // ==================== Business Methods ====================
    
    /**
     * Get the complete location string
     * 
     * @return formatted location string
     */
    public String getCompleteLocation() {
        StringBuilder location = new StringBuilder();
        
        if (this.location != null && !this.location.trim().isEmpty()) {
            location.append(this.location.trim());
        }
        
        if (city != null && !city.trim().isEmpty()) {
            if (location.length() > 0) {
                location.append(", ");
            }
            location.append(city.trim());
        }
        
        if (state != null && !state.trim().isEmpty()) {
            if (location.length() > 0) {
                location.append(", ");
            }
            location.append(state.trim());
        }
        
        if (country != null && !country.trim().isEmpty()) {
            if (location.length() > 0) {
                location.append(", ");
            }
            location.append(country.trim());
        }
        
        return location.toString();
    }
    
    /**
     * Check if this tracking update indicates delivery
     * 
     * @return true if this indicates delivery
     */
    public boolean isDeliveryUpdate() {
        return trackingStatus != null && trackingStatus.isDelivered();
    }
    
    /**
     * Check if this tracking update indicates a problem
     * 
     * @return true if this indicates a problem
     */
    public boolean isProblemUpdate() {
        return trackingStatus != null && trackingStatus.isProblem();
    }
    
    /**
     * Check if this tracking update requires customer action
     * 
     * @return true if customer action is required
     */
    public boolean requiresCustomerAction() {
        return trackingStatus != null && trackingStatus.requiresCustomerAction();
    }
    
    /**
     * Get the progress percentage for this tracking status
     * 
     * @return progress percentage (0-100)
     */
    public int getProgressPercentage() {
        return trackingStatus != null ? trackingStatus.getProgressPercentage() : 0;
    }
    
    /**
     * Check if this is a delivery attempt
     * 
     * @return true if this is a delivery attempt
     */
    public boolean isDeliveryAttempt() {
        return deliveryAttempt != null && deliveryAttempt > 0;
    }
    
    /**
     * Check if delivery was successful
     * 
     * @return true if delivery was successful
     */
    public boolean isDeliverySuccessful() {
        return isDeliveryUpdate() && actualDeliveryDate != null;
    }
    
    /**
     * Check if delivery failed
     * 
     * @return true if delivery failed
     */
    public boolean isDeliveryFailed() {
        return isDeliveryAttempt() && !isDeliverySuccessful() && 
               deliveryFailureReason != null && !deliveryFailureReason.trim().isEmpty();
    }
    
    /**
     * Get the time since this tracking update
     * 
     * @return hours since this update
     */
    public long getHoursSinceUpdate() {
        return java.time.Duration.between(timestamp, LocalDateTime.now()).toHours();
    }
    
    /**
     * Check if this update is recent (within last 24 hours)
     * 
     * @return true if recent
     */
    public boolean isRecent() {
        return getHoursSinceUpdate() <= 24;
    }
    
    /**
     * Soft delete this tracking record
     */
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
    
    /**
     * Check if this tracking record is soft deleted
     * 
     * @return true if soft deleted
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }
}
