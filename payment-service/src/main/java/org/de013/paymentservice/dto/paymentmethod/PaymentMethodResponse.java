package org.de013.paymentservice.dto.paymentmethod;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.de013.paymentservice.entity.enums.PaymentMethodType;

import java.time.LocalDateTime;

/**
 * Response DTO for payment method information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethodResponse {

    private Long id;
    private Long userId;
    private PaymentMethodType type;
    private String provider;
    private Boolean isDefault;
    private Boolean isActive;

    // Display information (never include sensitive data)
    private String displayName;
    private String nickname;

    // Card information (masked/safe data only)
    private CardInfo cardInfo;

    // Wallet information
    private WalletInfo walletInfo;

    // Billing information
    private BillingAddress billingAddress;

    // Usage information
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastUsedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    // Status information
    private Boolean isExpired;
    private Boolean canBeUsed;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardInfo {
        private String maskedNumber; // e.g., "**** **** **** 1234"
        private String brand; // e.g., "visa", "mastercard"
        private Integer expiryMonth;
        private Integer expiryYear;
        private String country;
        private String funding; // "credit", "debit", "prepaid"
        private Boolean isExpired;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WalletInfo {
        private String type; // "apple_pay", "google_pay", etc.
        private String displayName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BillingAddress {
        private String customerName;
        private String line1;
        private String line2;
        private String city;
        private String state;
        private String postalCode;
        private String country;
    }

    // Helper methods for frontend display
    public String getTypeDisplayName() {
        return switch (type) {
            case CARD -> "Card";
            case BANK_ACCOUNT -> "Bank Account";
            case WALLET -> "Digital Wallet";
            case BUY_NOW_PAY_LATER -> "Buy Now, Pay Later";
            case BANK_TRANSFER -> "Bank Transfer";
            case OTHER -> "Other";
        };
    }

    public String getStatusDisplayName() {
        if (!isActive) {
            return "Inactive";
        }
        if (isExpired != null && isExpired) {
            return "Expired";
        }
        if (isDefault) {
            return "Default";
        }
        return "Active";
    }

    public String getShortDescription() {
        if (nickname != null && !nickname.trim().isEmpty()) {
            return nickname;
        }

        if (type == PaymentMethodType.CARD && cardInfo != null) {
            String brand = cardInfo.getBrand() != null ? 
                cardInfo.getBrand().toUpperCase() : "CARD";
            String last4 = cardInfo.getMaskedNumber() != null ? 
                cardInfo.getMaskedNumber().substring(cardInfo.getMaskedNumber().length() - 4) : "****";
            return brand + " •••• " + last4;
        }

        if (type == PaymentMethodType.WALLET && walletInfo != null) {
            return walletInfo.getDisplayName() != null ? 
                walletInfo.getDisplayName() : walletInfo.getType();
        }

        return getTypeDisplayName();
    }

    public boolean canBeDeleted() {
        return isActive && !isDefault;
    }

    public boolean canBeSetAsDefault() {
        return isActive && !isDefault && (isExpired == null || !isExpired);
    }

    public boolean needsUpdate() {
        return isExpired != null && isExpired;
    }
}
