package org.de013.orderservice.entity;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import org.de013.common.entity.BaseEntity;
import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.entity.enums.OrderType;
import org.de013.orderservice.entity.valueobject.Address;
import org.de013.orderservice.entity.valueobject.Money;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Order Entity
 *
 * Represents a customer order in the e-commerce system.
 * This is the main aggregate root for order management.
 *
 * @author Development Team
 * @version 1.0.0
 */
@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_order_number", columnList = "orderNumber", unique = true),
    @Index(name = "idx_user_id", columnList = "userId"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_order_type", columnList = "orderType"),
    @Index(name = "idx_created_at", columnList = "createdAt"),
    @Index(name = "idx_user_status", columnList = "userId, status"),
    @Index(name = "idx_status_created", columnList = "status, createdAt")
})
@SQLDelete(sql = "UPDATE orders SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"orderItems", "orderTracking", "orderPayments", "orderShipping"})
@EqualsAndHashCode(callSuper = true, exclude = {"orderItems", "orderTracking", "orderPayments", "orderShipping"})
public class Order extends BaseEntity {

    /**
     * Unique order number for customer reference
     */
    @Column(name = "order_number", length = 50, nullable = false, unique = true)
    @NotBlank(message = "Order number is required")
    @Size(max = 50, message = "Order number must not exceed 50 characters")
    private String orderNumber;

    /**
     * ID of the user who placed the order
     */
    @Column(name = "user_id", nullable = false)
    @NotNull(message = "User ID is required")
    @Positive(message = "User ID must be positive")
    private Long userId;

    /**
     * Current status of the order
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    @NotNull(message = "Order status is required")
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    /**
     * Type of the order
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", length = 20, nullable = false)
    @NotNull(message = "Order type is required")
    @Builder.Default
    private OrderType orderType = OrderType.STANDARD;

    /**
     * Total amount of the order
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "total_amount", precision = 19, scale = 4)),
        @AttributeOverride(name = "currency", column = @Column(name = "currency", length = 3))
    })
    @Valid
    @NotNull(message = "Total amount is required")
    private Money totalAmount;

    /**
     * Subtotal amount (before taxes and fees)
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "subtotal_amount", precision = 19, scale = 4)),
        @AttributeOverride(name = "currency", column = @Column(name = "subtotal_currency", length = 3))
    })
    @Valid
    private Money subtotalAmount;

    /**
     * Tax amount
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "tax_amount", precision = 19, scale = 4)),
        @AttributeOverride(name = "currency", column = @Column(name = "tax_currency", length = 3))
    })
    @Valid
    private Money taxAmount;

    /**
     * Shipping amount
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "shipping_amount", precision = 19, scale = 4)),
        @AttributeOverride(name = "currency", column = @Column(name = "shipping_currency", length = 3))
    })
    @Valid
    private Money shippingAmount;

    /**
     * Discount amount
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "discount_amount", precision = 19, scale = 4)),
        @AttributeOverride(name = "currency", column = @Column(name = "discount_currency", length = 3))
    })
    @Valid
    private Money discountAmount;

    /**
     * Shipping address
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
     * Billing address
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "firstName", column = @Column(name = "billing_first_name")),
        @AttributeOverride(name = "lastName", column = @Column(name = "billing_last_name")),
        @AttributeOverride(name = "company", column = @Column(name = "billing_company")),
        @AttributeOverride(name = "streetAddress", column = @Column(name = "billing_street_address")),
        @AttributeOverride(name = "streetAddress2", column = @Column(name = "billing_street_address_2")),
        @AttributeOverride(name = "city", column = @Column(name = "billing_city")),
        @AttributeOverride(name = "state", column = @Column(name = "billing_state")),
        @AttributeOverride(name = "postalCode", column = @Column(name = "billing_postal_code")),
        @AttributeOverride(name = "country", column = @Column(name = "billing_country")),
        @AttributeOverride(name = "phone", column = @Column(name = "billing_phone")),
        @AttributeOverride(name = "email", column = @Column(name = "billing_email")),
        @AttributeOverride(name = "deliveryInstructions", column = @Column(name = "billing_delivery_instructions")),
        @AttributeOverride(name = "addressType", column = @Column(name = "billing_address_type")),
        @AttributeOverride(name = "isResidential", column = @Column(name = "billing_is_residential"))
    })
    @Valid
    private Address billingAddress;

    /**
     * Customer notes for the order
     */
    @Column(name = "customer_notes", length = 2000)
    @Size(max = 2000, message = "Customer notes must not exceed 2000 characters")
    private String customerNotes;

    /**
     * Internal notes for the order
     */
    @Column(name = "internal_notes", length = 2000)
    @Size(max = 2000, message = "Internal notes must not exceed 2000 characters")
    private String internalNotes;

    /**
     * Source of the order (WEB, MOBILE, API, etc.)
     */
    @Column(name = "order_source", length = 20)
    @Size(max = 20, message = "Order source must not exceed 20 characters")
    @Builder.Default
    private String orderSource = "WEB";

    /**
     * Expected delivery date
     */
    @Column(name = "expected_delivery_date")
    private LocalDateTime expectedDeliveryDate;

    /**
     * Actual delivery date
     */
    @Column(name = "actual_delivery_date")
    private LocalDateTime actualDeliveryDate;

    /**
     * Order confirmation date
     */
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /**
     * Order cancellation date
     */
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /**
     * Cancellation reason
     */
    @Column(name = "cancellation_reason", length = 500)
    @Size(max = 500, message = "Cancellation reason must not exceed 500 characters")
    private String cancellationReason;

    /**
     * Priority level (1 = highest, 5 = lowest)
     */
    @Column(name = "priority_level")
    @Min(value = 1, message = "Priority level must be at least 1")
    @Max(value = 5, message = "Priority level must be at most 5")
    @Builder.Default
    private Integer priorityLevel = 3;

    /**
     * Whether the order requires special handling
     */
    @Column(name = "requires_special_handling")
    @Builder.Default
    private Boolean requiresSpecialHandling = false;

    /**
     * Whether the order is a gift
     */
    @Column(name = "is_gift")
    @Builder.Default
    private Boolean isGift = false;

    /**
     * Gift message
     */
    @Column(name = "gift_message", length = 1000)
    @Size(max = 1000, message = "Gift message must not exceed 1000 characters")
    private String giftMessage;

    /**
     * Soft delete timestamp
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * Order items
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();

    /**
     * Order tracking records
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    @Builder.Default
    private List<OrderTracking> orderTracking = new ArrayList<>();

    /**
     * Order payments
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    @Builder.Default
    private List<OrderPayment> orderPayments = new ArrayList<>();

    /**
     * Order shipping information
     */
    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private OrderShipping orderShipping;

    // ==================== Business Methods ====================

    /**
     * Add an order item to this order
     *
     * @param orderItem the order item to add
     */
    public void addOrderItem(OrderItem orderItem) {
        if (orderItem != null) {
            orderItems.add(orderItem);
            orderItem.setOrder(this);
        }
    }

    /**
     * Remove an order item from this order
     *
     * @param orderItem the order item to remove
     */
    public void removeOrderItem(OrderItem orderItem) {
        if (orderItem != null) {
            orderItems.remove(orderItem);
            orderItem.setOrder(null);
        }
    }

    /**
     * Add order tracking record
     *
     * @param tracking the tracking record to add
     */
    public void addOrderTracking(OrderTracking tracking) {
        if (tracking != null) {
            orderTracking.add(tracking);
            tracking.setOrder(this);
        }
    }

    /**
     * Add order payment record
     *
     * @param payment the payment record to add
     */
    public void addOrderPayment(OrderPayment payment) {
        if (payment != null) {
            orderPayments.add(payment);
            payment.setOrder(this);
        }
    }

    /**
     * Set order shipping information
     *
     * @param shipping the shipping information
     */
    public void setOrderShipping(OrderShipping shipping) {
        this.orderShipping = shipping;
        if (shipping != null) {
            shipping.setOrder(this);
        }
    }

    /**
     * Calculate total quantity of items in the order
     *
     * @return total quantity
     */
    public Integer getTotalQuantity() {
        return orderItems.stream()
                .mapToInt(OrderItem::getQuantity)
                .sum();
    }

    /**
     * Calculate total number of unique items in the order
     *
     * @return number of unique items
     */
    public Integer getTotalUniqueItems() {
        return orderItems.size();
    }

    /**
     * Check if the order can be cancelled
     *
     * @return true if order can be cancelled
     */
    public boolean canBeCancelled() {
        return status != null && status.canTransitionTo(OrderStatus.CANCELLED);
    }

    /**
     * Check if the order can be modified
     *
     * @return true if order can be modified
     */
    public boolean canBeModified() {
        return status == OrderStatus.PENDING || status == OrderStatus.CONFIRMED;
    }

    /**
     * Check if the order is in a final state
     *
     * @return true if order is in final state
     */
    public boolean isFinalState() {
        return status != null && status.isFinal();
    }

    /**
     * Check if the order is active
     *
     * @return true if order is active
     */
    public boolean isActive() {
        return status != null && status.isActive();
    }

    /**
     * Check if the order has been delivered
     *
     * @return true if order has been delivered
     */
    public boolean isDelivered() {
        return status == OrderStatus.DELIVERED || status == OrderStatus.COMPLETED;
    }

    /**
     * Check if the order is paid
     *
     * @return true if order is paid
     */
    public boolean isPaid() {
        return orderPayments.stream()
                .anyMatch(payment -> payment.getStatus() != null &&
                         payment.getStatus().isSuccessful());
    }

    /**
     * Get the latest tracking status
     *
     * @return latest tracking status or null
     */
    public OrderTracking getLatestTracking() {
        return orderTracking.stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * Get the latest payment
     *
     * @return latest payment or null
     */
    public OrderPayment getLatestPayment() {
        return orderPayments.stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * Check if billing address is same as shipping address
     *
     * @return true if addresses are the same
     */
    public boolean isBillingSameAsShipping() {
        return billingAddress != null && billingAddress.equals(shippingAddress);
    }

    /**
     * Check if the order requires expedited processing
     *
     * @return true if expedited processing is required
     */
    public boolean requiresExpeditedProcessing() {
        return orderType != null && orderType.requiresImmediateProcessing();
    }

    /**
     * Get the order age in hours
     *
     * @return order age in hours
     */
    public long getOrderAgeInHours() {
        if (getCreatedAt() != null) {
            return java.time.Duration.between(getCreatedAt(), LocalDateTime.now()).toHours();
        }
        return 0;
    }

    /**
     * Check if the order is overdue based on expected delivery date
     *
     * @return true if order is overdue
     */
    public boolean isOverdue() {
        return expectedDeliveryDate != null &&
               LocalDateTime.now().isAfter(expectedDeliveryDate) &&
               !isDelivered();
    }

    /**
     * Confirm the order
     */
    public void confirm() {
        if (status == OrderStatus.PENDING) {
            this.status = OrderStatus.CONFIRMED;
            this.confirmedAt = LocalDateTime.now();
        }
    }

    /**
     * Cancel the order
     *
     * @param reason the cancellation reason
     */
    public void cancel(String reason) {
        if (canBeCancelled()) {
            this.status = OrderStatus.CANCELLED;
            this.cancelledAt = LocalDateTime.now();
            this.cancellationReason = reason;
        }
    }

    /**
     * Mark the order as delivered
     *
     * @param deliveryDate the delivery date
     */
    public void markAsDelivered(LocalDateTime deliveryDate) {
        if (status.canTransitionTo(OrderStatus.DELIVERED)) {
            this.status = OrderStatus.DELIVERED;
            this.actualDeliveryDate = deliveryDate != null ? deliveryDate : LocalDateTime.now();
        }
    }

    /**
     * Update order status
     *
     * @param newStatus the new status
     * @throws IllegalArgumentException if transition is not allowed
     */
    public void updateStatus(OrderStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("New status cannot be null");
        }

        if (status == null || status.canTransitionTo(newStatus)) {
            this.status = newStatus;
        } else {
            throw new IllegalArgumentException(
                String.format("Cannot transition from %s to %s", status, newStatus));
        }
    }

    /**
     * Soft delete the order
     */
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    /**
     * Check if the order is soft deleted
     *
     * @return true if soft deleted
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }
}
