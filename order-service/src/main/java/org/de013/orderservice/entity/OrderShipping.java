package org.de013.orderservice.entity;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import org.de013.common.entity.BaseEntity;
import org.de013.orderservice.entity.valueobject.Address;
import org.de013.orderservice.entity.valueobject.Money;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Order Shipping Entity
 *
 * Represents shipping information and details for an order.
 * Contains carrier information, tracking details, and shipping costs.
 *
 * @author Development Team
 * @version 1.0.0
 */
@Entity
@Table(name = "order_shipping", indexes = {
    @Index(name = "idx_order_shipping_order_id", columnList = "order_id"),
    @Index(name = "idx_order_shipping_tracking_number", columnList = "tracking_number"),
    @Index(name = "idx_order_shipping_carrier", columnList = "carrier"),
    @Index(name = "idx_order_shipping_shipping_method", columnList = "shipping_method"),
    @Index(name = "idx_order_shipping_shipped_at", columnList = "shipped_at"),
    @Index(name = "idx_order_shipping_delivery_date", columnList = "estimated_delivery_date")
})
@SQLDelete(sql = "UPDATE order_shipping SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"order"})
@EqualsAndHashCode(callSuper = true, exclude = {"order"})
public class OrderShipping extends BaseEntity {

    /**
     * Reference to the parent order (one-to-one relationship)
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @NotNull(message = "Order is required")
    private Order order;

    /**
     * Shipping address (copy from order for historical purposes)
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "firstName", column = @Column(name = "shipping_first_name")),
        @AttributeOverride(name = "lastName", column = @Column(name = "shipping_last_name")),
        @AttributeOverride(name = "company", column = @Column(name = "shipping_company")),
        @AttributeOverride(name = "streetAddress", column = @Column(name = "shipping_street_address")),
        @AttributeOverride(name = "streetAddress2", column = @Column(name = "shipping_street_address_2")),
        @AttributeOverride(name = "city", column = @Column(name = "shipping_city")),
        @AttributeOverride(name = "state", column = @Column(name = "shipping_state")),
        @AttributeOverride(name = "postalCode", column = @Column(name = "shipping_postal_code")),
        @AttributeOverride(name = "country", column = @Column(name = "shipping_country")),
        @AttributeOverride(name = "phone", column = @Column(name = "shipping_phone")),
        @AttributeOverride(name = "email", column = @Column(name = "shipping_email")),
        @AttributeOverride(name = "deliveryInstructions", column = @Column(name = "shipping_delivery_instructions")),
        @AttributeOverride(name = "addressType", column = @Column(name = "shipping_address_type")),
        @AttributeOverride(name = "isResidential", column = @Column(name = "shipping_is_residential"))
    })
    @Valid
    @NotNull(message = "Shipping address is required")
    private Address shippingAddress;

    /**
     * Shipping method (STANDARD, EXPRESS, OVERNIGHT, etc.)
     */
    @Column(name = "shipping_method", length = 50, nullable = false)
    @NotBlank(message = "Shipping method is required")
    @Size(max = 50, message = "Shipping method must not exceed 50 characters")
    private String shippingMethod;

    /**
     * Shipping carrier (UPS, FedEx, DHL, USPS, etc.)
     */
    @Column(name = "carrier", length = 100)
    @Size(max = 100, message = "Carrier must not exceed 100 characters")
    private String carrier;

    /**
     * Carrier service type (Ground, Air, Express, etc.)
     */
    @Column(name = "carrier_service", length = 100)
    @Size(max = 100, message = "Carrier service must not exceed 100 characters")
    private String carrierService;

    /**
     * Tracking number provided by carrier
     */
    @Column(name = "tracking_number", length = 100)
    @Size(max = 100, message = "Tracking number must not exceed 100 characters")
    private String trackingNumber;

    /**
     * Carrier tracking URL
     */
    @Column(name = "tracking_url", length = 1000)
    @Size(max = 1000, message = "Tracking URL must not exceed 1000 characters")
    private String trackingUrl;

    /**
     * Shipping cost
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "shipping_cost", precision = 19, scale = 4)),
        @AttributeOverride(name = "currency", column = @Column(name = "shipping_currency", length = 3))
    })
    @Valid
    private Money shippingCost;

    /**
     * Insurance cost
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "insurance_cost", precision = 19, scale = 4)),
        @AttributeOverride(name = "currency", column = @Column(name = "insurance_currency", length = 3))
    })
    @Valid
    private Money insuranceCost;

    /**
     * Total weight of the shipment
     */
    @Column(name = "total_weight", precision = 10, scale = 3)
    @DecimalMin(value = "0.0", message = "Total weight must be non-negative")
    private java.math.BigDecimal totalWeight;

    /**
     * Weight unit (kg, lb, etc.)
     */
    @Column(name = "weight_unit", length = 10)
    @Size(max = 10, message = "Weight unit must not exceed 10 characters")
    @Builder.Default
    private String weightUnit = "kg";

    /**
     * Package dimensions (length x width x height)
     */
    @Column(name = "dimensions", length = 100)
    @Size(max = 100, message = "Dimensions must not exceed 100 characters")
    private String dimensions;

    /**
     * Number of packages in this shipment
     */
    @Column(name = "package_count")
    @Min(value = 1, message = "Package count must be at least 1")
    @Builder.Default
    private Integer packageCount = 1;

    /**
     * Estimated delivery date
     */
    @Column(name = "estimated_delivery_date")
    private LocalDateTime estimatedDeliveryDate;

    /**
     * Actual delivery date
     */
    @Column(name = "actual_delivery_date")
    private LocalDateTime actualDeliveryDate;

    /**
     * Date when the order was shipped
     */
    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    /**
     * Date when shipping label was created
     */
    @Column(name = "label_created_at")
    private LocalDateTime labelCreatedAt;

    /**
     * Shipping label URL or file path
     */
    @Column(name = "shipping_label_url", length = 1000)
    @Size(max = 1000, message = "Shipping label URL must not exceed 1000 characters")
    private String shippingLabelUrl;

    /**
     * Commercial invoice URL (for international shipments)
     */
    @Column(name = "commercial_invoice_url", length = 1000)
    @Size(max = 1000, message = "Commercial invoice URL must not exceed 1000 characters")
    private String commercialInvoiceUrl;

    /**
     * Customs declaration number (for international shipments)
     */
    @Column(name = "customs_declaration_number", length = 100)
    @Size(max = 100, message = "Customs declaration number must not exceed 100 characters")
    private String customsDeclarationNumber;

    /**
     * Whether signature is required for delivery
     */
    @Column(name = "signature_required")
    @Builder.Default
    private Boolean signatureRequired = false;

    /**
     * Whether adult signature is required
     */
    @Column(name = "adult_signature_required")
    @Builder.Default
    private Boolean adultSignatureRequired = false;

    /**
     * Whether the shipment is insured
     */
    @Column(name = "is_insured")
    @Builder.Default
    private Boolean isInsured = false;

    /**
     * Insurance value
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "insurance_value", precision = 19, scale = 4)),
        @AttributeOverride(name = "currency", column = @Column(name = "insurance_value_currency", length = 3))
    })
    @Valid
    private Money insuranceValue;

    /**
     * Whether this is an international shipment
     */
    @Column(name = "is_international")
    @Builder.Default
    private Boolean isInternational = false;

    /**
     * Whether delivery confirmation is required
     */
    @Column(name = "delivery_confirmation_required")
    @Builder.Default
    private Boolean deliveryConfirmationRequired = false;

    /**
     * Special handling instructions
     */
    @Column(name = "special_instructions", length = 2000)
    @Size(max = 2000, message = "Special instructions must not exceed 2000 characters")
    private String specialInstructions;

    /**
     * Delivery window start time
     */
    @Column(name = "delivery_window_start")
    private LocalDateTime deliveryWindowStart;

    /**
     * Delivery window end time
     */
    @Column(name = "delivery_window_end")
    private LocalDateTime deliveryWindowEnd;

    /**
     * Preferred delivery date requested by customer
     */
    @Column(name = "preferred_delivery_date")
    private LocalDateTime preferredDeliveryDate;

    /**
     * Shipping status (PENDING, LABEL_CREATED, SHIPPED, IN_TRANSIT, DELIVERED, etc.)
     */
    @Column(name = "shipping_status", length = 30)
    @Size(max = 30, message = "Shipping status must not exceed 30 characters")
    @Builder.Default
    private String shippingStatus = "PENDING";

    /**
     * Return tracking number (if applicable)
     */
    @Column(name = "return_tracking_number", length = 100)
    @Size(max = 100, message = "Return tracking number must not exceed 100 characters")
    private String returnTrackingNumber;

    /**
     * Return shipping label URL
     */
    @Column(name = "return_label_url", length = 1000)
    @Size(max = 1000, message = "Return label URL must not exceed 1000 characters")
    private String returnLabelUrl;

    /**
     * Additional metadata in JSON format
     */
    @Column(name = "metadata", length = 2000)
    @Size(max = 2000, message = "Metadata must not exceed 2000 characters")
    private String metadata;

    /**
     * Soft delete timestamp
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // ==================== Business Methods ====================

    /**
     * Get total shipping cost including insurance
     *
     * @return total shipping cost
     */
    public Money getTotalShippingCost() {
        Money total = shippingCost != null ? shippingCost : Money.zero("USD");

        if (insuranceCost != null && insuranceCost.isPositive()) {
            total = total.add(insuranceCost);
        }

        return total;
    }

    /**
     * Check if the shipment has been shipped
     *
     * @return true if shipped
     */
    public boolean isShipped() {
        return shippedAt != null && trackingNumber != null && !trackingNumber.trim().isEmpty();
    }

    /**
     * Check if the shipment has been delivered
     *
     * @return true if delivered
     */
    public boolean isDelivered() {
        return "DELIVERED".equals(shippingStatus) && actualDeliveryDate != null;
    }

    /**
     * Check if the shipment is in transit
     *
     * @return true if in transit
     */
    public boolean isInTransit() {
        return isShipped() && !isDelivered() &&
               ("IN_TRANSIT".equals(shippingStatus) || "SHIPPED".equals(shippingStatus));
    }

    /**
     * Check if the shipment is overdue
     *
     * @return true if overdue
     */
    public boolean isOverdue() {
        return estimatedDeliveryDate != null &&
               LocalDateTime.now().isAfter(estimatedDeliveryDate) &&
               !isDelivered();
    }

    /**
     * Get delivery time in hours (from shipped to delivered)
     *
     * @return delivery time in hours
     */
    public long getDeliveryTimeHours() {
        if (shippedAt != null && actualDeliveryDate != null) {
            return java.time.Duration.between(shippedAt, actualDeliveryDate).toHours();
        }
        return 0;
    }

    /**
     * Check if delivery was fast (within expected time)
     *
     * @return true if delivery was fast
     */
    public boolean isFastDelivery() {
        if (estimatedDeliveryDate != null && actualDeliveryDate != null) {
            return actualDeliveryDate.isBefore(estimatedDeliveryDate) ||
                   actualDeliveryDate.isEqual(estimatedDeliveryDate);
        }
        return false;
    }

    /**
     * Check if this is an express shipment
     *
     * @return true if express
     */
    public boolean isExpress() {
        return shippingMethod != null &&
               (shippingMethod.toUpperCase().contains("EXPRESS") ||
                shippingMethod.toUpperCase().contains("OVERNIGHT") ||
                shippingMethod.toUpperCase().contains("NEXT_DAY"));
    }

    /**
     * Check if shipping label has been created
     *
     * @return true if label created
     */
    public boolean isLabelCreated() {
        return labelCreatedAt != null &&
               shippingLabelUrl != null && !shippingLabelUrl.trim().isEmpty();
    }

    /**
     * Check if the shipment requires special handling
     *
     * @return true if special handling required
     */
    public boolean requiresSpecialHandling() {
        return specialInstructions != null && !specialInstructions.trim().isEmpty() ||
               signatureRequired || adultSignatureRequired || isInsured;
    }

    /**
     * Check if delivery is within the preferred window
     *
     * @return true if within delivery window
     */
    public boolean isWithinDeliveryWindow() {
        if (actualDeliveryDate == null || deliveryWindowStart == null || deliveryWindowEnd == null) {
            return true; // No window specified or not delivered yet
        }

        return !actualDeliveryDate.isBefore(deliveryWindowStart) &&
               !actualDeliveryDate.isAfter(deliveryWindowEnd);
    }

    /**
     * Get estimated delivery days from ship date
     *
     * @return estimated delivery days
     */
    public long getEstimatedDeliveryDays() {
        if (shippedAt != null && estimatedDeliveryDate != null) {
            return java.time.Duration.between(shippedAt, estimatedDeliveryDate).toDays();
        }
        return 0;
    }

    /**
     * Check if customs documentation is complete (for international shipments)
     *
     * @return true if customs documentation is complete
     */
    public boolean isCustomsDocumentationComplete() {
        if (!isInternational) {
            return true; // Not applicable for domestic shipments
        }

        return commercialInvoiceUrl != null && !commercialInvoiceUrl.trim().isEmpty() &&
               customsDeclarationNumber != null && !customsDeclarationNumber.trim().isEmpty();
    }

    /**
     * Mark as shipped
     *
     * @param trackingNumber the tracking number
     * @param shippedDate the shipped date
     */
    public void markAsShipped(String trackingNumber, LocalDateTime shippedDate) {
        this.trackingNumber = trackingNumber;
        this.shippedAt = shippedDate != null ? shippedDate : LocalDateTime.now();
        this.shippingStatus = "SHIPPED";
    }

    /**
     * Mark as delivered
     *
     * @param deliveryDate the delivery date
     */
    public void markAsDelivered(LocalDateTime deliveryDate) {
        this.actualDeliveryDate = deliveryDate != null ? deliveryDate : LocalDateTime.now();
        this.shippingStatus = "DELIVERED";
    }

    /**
     * Update shipping status
     *
     * @param newStatus the new shipping status
     */
    public void updateShippingStatus(String newStatus) {
        this.shippingStatus = newStatus;

        // Update relevant timestamps based on status
        LocalDateTime now = LocalDateTime.now();
        switch (newStatus.toUpperCase()) {
            case "SHIPPED" -> {
                if (this.shippedAt == null) {
                    this.shippedAt = now;
                }
            }
            case "DELIVERED" -> {
                if (this.actualDeliveryDate == null) {
                    this.actualDeliveryDate = now;
                }
            }
        }
    }

    /**
     * Create shipping label
     *
     * @param labelUrl the shipping label URL
     */
    public void createShippingLabel(String labelUrl) {
        this.shippingLabelUrl = labelUrl;
        this.labelCreatedAt = LocalDateTime.now();
        this.shippingStatus = "LABEL_CREATED";
    }

    /**
     * Add insurance
     *
     * @param insuranceValue the insurance value
     * @param insuranceCost the insurance cost
     */
    public void addInsurance(Money insuranceValue, Money insuranceCost) {
        this.insuranceValue = insuranceValue;
        this.insuranceCost = insuranceCost;
        this.isInsured = true;
    }

    /**
     * Set delivery window
     *
     * @param startTime the window start time
     * @param endTime the window end time
     */
    public void setDeliveryWindow(LocalDateTime startTime, LocalDateTime endTime) {
        this.deliveryWindowStart = startTime;
        this.deliveryWindowEnd = endTime;
    }

    /**
     * Generate tracking URL based on carrier
     *
     * @return tracking URL
     */
    public String generateTrackingUrl() {
        if (trackingNumber == null || trackingNumber.trim().isEmpty() || carrier == null) {
            return null;
        }

        String baseUrl = switch (carrier.toUpperCase()) {
            case "UPS" -> "https://www.ups.com/track?tracknum=" + trackingNumber;
            case "FEDEX" -> "https://www.fedex.com/apps/fedextrack/?tracknumbers=" + trackingNumber;
            case "DHL" -> "https://www.dhl.com/en/express/tracking.html?AWB=" + trackingNumber;
            case "USPS" -> "https://tools.usps.com/go/TrackConfirmAction?qtc_tLabels1=" + trackingNumber;
            default -> trackingUrl; // Use existing URL if available
        };

        if (baseUrl != null && !baseUrl.equals(trackingUrl)) {
            this.trackingUrl = baseUrl;
        }

        return this.trackingUrl;
    }

    /**
     * Soft delete this shipping record
     */
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    /**
     * Check if this shipping record is soft deleted
     *
     * @return true if soft deleted
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }
}
