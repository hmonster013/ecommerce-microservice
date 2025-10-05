package org.de013.paymentservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import org.de013.paymentservice.entity.enums.PaymentMethodType;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * PaymentMethod entity representing saved payment methods for users
 */
@Entity
@Table(name = "payment_methods", indexes = {
    @Index(name = "idx_user_id", columnList = "userId"),
    @Index(name = "idx_user_id_is_active", columnList = "userId, isActive"),
    @Index(name = "idx_user_id_is_default", columnList = "userId, isDefault"),
    @Index(name = "idx_stripe_payment_method_id", columnList = "stripePaymentMethodId", unique = true),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PaymentMethod extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private PaymentMethodType type;

    @Column(name = "provider", nullable = false, length = 50)
    @Builder.Default
    private String provider = "STRIPE";

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // Stripe-specific fields
    @Column(name = "stripe_payment_method_id", unique = true, length = 100)
    private String stripePaymentMethodId;

    @Column(name = "stripe_customer_id", length = 100)
    private String stripeCustomerId;

    // Card details (for display purposes only - never store sensitive data)
    @Column(name = "masked_card_number", length = 20)
    private String maskedCardNumber; // e.g., "**** **** **** 1234"

    @Column(name = "card_brand", length = 20)
    private String cardBrand; // e.g., "visa", "mastercard"

    @Column(name = "expiry_month")
    private Integer expiryMonth;

    @Column(name = "expiry_year")
    private Integer expiryYear;

    @Column(name = "card_country", length = 2)
    private String cardCountry; // ISO country code

    @Column(name = "card_funding", length = 20)
    private String cardFunding; // "credit", "debit", "prepaid"

    // Customer info
    @Column(name = "customer_name", length = 100)
    private String customerName;

    @Column(name = "billing_address_line1", length = 200)
    private String billingAddressLine1;

    @Column(name = "billing_address_line2", length = 200)
    private String billingAddressLine2;

    @Column(name = "billing_city", length = 100)
    private String billingCity;

    @Column(name = "billing_state", length = 100)
    private String billingState;

    @Column(name = "billing_postal_code", length = 20)
    private String billingPostalCode;

    @Column(name = "billing_country", length = 2)
    private String billingCountry; // ISO country code

    // Digital wallet info (for non-card payment methods)
    @Column(name = "wallet_type", length = 30)
    private String walletType; // "apple_pay", "google_pay", etc.

    @Column(name = "wallet_id", length = 100)
    private String walletId;

    // Additional metadata
    @Column(name = "nickname", length = 50)
    private String nickname; // User-friendly name for the payment method

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    // Helper methods
    public String getDisplayName() {
        if (nickname != null && !nickname.trim().isEmpty()) {
            return nickname;
        }
        
        if (type == PaymentMethodType.CARD && maskedCardNumber != null) {
            return cardBrand != null ? 
                cardBrand.toUpperCase() + " " + maskedCardNumber : 
                maskedCardNumber;
        }
        
        if (type == PaymentMethodType.WALLET && walletType != null) {
            return walletType.replace("_", " ").toUpperCase();
        }
        
        return type.toString();
    }

    public boolean isExpired() {
        if (expiryMonth == null || expiryYear == null) {
            return false;
        }
        
        LocalDateTime now = LocalDateTime.now();
        return now.getYear() > expiryYear || 
               (now.getYear() == expiryYear && now.getMonthValue() > expiryMonth);
    }

    public void updateLastUsed() {
        this.lastUsedAt = LocalDateTime.now();
    }
}
