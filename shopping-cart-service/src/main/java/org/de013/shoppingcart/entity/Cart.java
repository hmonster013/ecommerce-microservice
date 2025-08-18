package org.de013.shoppingcart.entity;

import jakarta.persistence.*;
import lombok.*;
import org.de013.shoppingcart.entity.enums.CartStatus;
import org.de013.shoppingcart.entity.enums.CartType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Cart Entity
 * Represents a shopping cart in the e-commerce system
 */
@Entity
@Table(name = "carts", indexes = {
    @Index(name = "idx_cart_user_id", columnList = "user_id"),
    @Index(name = "idx_cart_session_id", columnList = "session_id"),
    @Index(name = "idx_cart_status", columnList = "status"),
    @Index(name = "idx_cart_type", columnList = "cart_type"),
    @Index(name = "idx_cart_expires_at", columnList = "expires_at"),
    @Index(name = "idx_cart_created_at", columnList = "created_at"),
    @Index(name = "idx_cart_user_status", columnList = "user_id, status"),
    @Index(name = "idx_cart_session_status", columnList = "session_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"cartItems"})
@EqualsAndHashCode(callSuper = true, exclude = {"cartItems"})
public class Cart extends BaseEntity {

    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CartStatus status = CartStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "cart_type", nullable = false, length = 20)
    @Builder.Default
    private CartType cartType = CartType.GUEST;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "currency", length = 3, nullable = false)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "subtotal", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "shipping_amount", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal shippingAmount = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "item_count", nullable = false)
    @Builder.Default
    private Integer itemCount = 0;

    @Column(name = "total_quantity", nullable = false)
    @Builder.Default
    private Integer totalQuantity = 0;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    @Column(name = "checkout_started_at")
    private LocalDateTime checkoutStartedAt;

    @Column(name = "converted_to_order_id", length = 36)
    private String convertedToOrderId;

    @Column(name = "merged_to_cart_id")
    private Long mergedToCartId;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<CartItem> cartItems = new ArrayList<>();

    /**
     * Add item to cart
     */
    public void addItem(CartItem item) {
        item.setCart(this);
        this.cartItems.add(item);
        updateCartTotals();
    }

    /**
     * Remove item from cart
     */
    public void removeItem(CartItem item) {
        this.cartItems.remove(item);
        item.setCart(null);
        updateCartTotals();
    }

    /**
     * Clear all items from cart
     */
    public void clearItems() {
        this.cartItems.clear();
        updateCartTotals();
    }

    /**
     * Update cart totals based on items
     */
    public void updateCartTotals() {
        this.itemCount = cartItems.size();
        this.totalQuantity = cartItems.stream()
                .mapToInt(CartItem::getQuantity)
                .sum();
        this.subtotal = cartItems.stream()
                .map(CartItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate total (subtotal + tax + shipping - discount)
        this.totalAmount = subtotal
                .add(taxAmount != null ? taxAmount : BigDecimal.ZERO)
                .add(shippingAmount != null ? shippingAmount : BigDecimal.ZERO)
                .subtract(discountAmount != null ? discountAmount : BigDecimal.ZERO);

        this.lastActivityAt = LocalDateTime.now();
    }

    /**
     * Check if cart is empty
     */
    public boolean isEmpty() {
        return cartItems.isEmpty();
    }

    /**
     * Check if cart is expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if cart can be modified
     */
    public boolean canBeModified() {
        return status.isModifiable() && !isExpired();
    }

    /**
     * Update last activity timestamp
     */
    public void updateLastActivity() {
        this.lastActivityAt = LocalDateTime.now();
    }

    /**
     * Set expiration time based on cart type
     */
    public void setExpirationFromType() {
        if (cartType != null) {
            this.expiresAt = LocalDateTime.now().plusSeconds(cartType.getDefaultTtlSeconds());
        }
    }
}
