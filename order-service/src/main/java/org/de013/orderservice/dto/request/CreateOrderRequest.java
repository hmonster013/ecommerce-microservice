package org.de013.orderservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import org.de013.orderservice.entity.enums.OrderType;
import org.de013.orderservice.entity.valueobject.Address;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Create Order Request DTO
 * 
 * Request object for creating a new order from shopping cart.
 * Contains all necessary information to process and create an order.
 * 
 * @author Development Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderRequest {
    
    /**
     * ID of the user placing the order
     */
    @NotNull(message = "User ID is required")
    @Positive(message = "User ID must be positive")
    private Long userId;
    
    /**
     * ID of the shopping cart to convert to order
     */
    @NotNull(message = "Cart ID is required")
    @Positive(message = "Cart ID must be positive")
    private Long cartId;
    
    /**
     * Type of order being placed
     */
    @NotNull(message = "Order type is required")
    private OrderType orderType;
    
    /**
     * Shipping address for the order
     */
    @Valid
    @NotNull(message = "Shipping address is required")
    private Address shippingAddress;
    
    /**
     * Billing address for the order (optional, defaults to shipping address)
     */
    @Valid
    private Address billingAddress;
    
    /**
     * Payment method information
     */
    @Valid
    @NotNull(message = "Payment method is required")
    private PaymentMethodDto paymentMethod;
    
    /**
     * Shipping method preference
     */
    @NotBlank(message = "Shipping method is required")
    @Size(max = 50, message = "Shipping method must not exceed 50 characters")
    private String shippingMethod;
    
    /**
     * Currency for the order
     */
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be uppercase letters")
    private String currency;
    
    /**
     * Customer notes for the order
     */
    @Size(max = 2000, message = "Customer notes must not exceed 2000 characters")
    private String customerNotes;
    
    /**
     * Whether this order is a gift
     */
    @Builder.Default
    private Boolean isGift = false;
    
    /**
     * Gift message if this is a gift order
     */
    @Size(max = 1000, message = "Gift message must not exceed 1000 characters")
    private String giftMessage;
    
    /**
     * Preferred delivery date
     */
    private LocalDateTime preferredDeliveryDate;
    
    /**
     * Special delivery instructions
     */
    @Size(max = 1000, message = "Delivery instructions must not exceed 1000 characters")
    private String deliveryInstructions;
    
    /**
     * Whether signature is required for delivery
     */
    @Builder.Default
    private Boolean signatureRequired = false;
    
    /**
     * Whether adult signature is required
     */
    @Builder.Default
    private Boolean adultSignatureRequired = false;
    
    /**
     * Whether to purchase shipping insurance
     */
    @Builder.Default
    private Boolean purchaseInsurance = false;
    
    /**
     * Insurance value if purchasing insurance
     */
    @DecimalMin(value = "0.0", message = "Insurance value must be non-negative")
    private BigDecimal insuranceValue;
    
    /**
     * Promotional code to apply
     */
    @Size(max = 50, message = "Promo code must not exceed 50 characters")
    private String promoCode;
    
    /**
     * Source of the order (WEB, MOBILE, API, etc.)
     */
    @Size(max = 20, message = "Order source must not exceed 20 characters")
    @Builder.Default
    private String orderSource = "WEB";
    
    /**
     * Customer IP address for fraud detection
     */
    @Size(max = 45, message = "Customer IP must not exceed 45 characters")
    private String customerIp;
    
    /**
     * Customer user agent for fraud detection
     */
    @Size(max = 1000, message = "Customer user agent must not exceed 1000 characters")
    private String customerUserAgent;
    
    /**
     * Whether to save payment method for future use
     */
    @Builder.Default
    private Boolean savePaymentMethod = false;
    
    /**
     * Whether to subscribe to order notifications
     */
    @Builder.Default
    private Boolean subscribeToNotifications = true;
    
    /**
     * Additional metadata for the order
     */
    private String metadata;
    
    /**
     * Payment Method DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaymentMethodDto {
        
        /**
         * Payment method type (CREDIT_CARD, DEBIT_CARD, PAYPAL, etc.)
         */
        @NotBlank(message = "Payment method type is required")
        @Size(max = 50, message = "Payment method type must not exceed 50 characters")
        private String type;
        
        /**
         * Payment token from payment gateway
         */
        @Size(max = 200, message = "Payment token must not exceed 200 characters")
        private String token;
        
        /**
         * Saved payment method ID (if using saved payment)
         */
        private Long savedPaymentMethodId;
        
        /**
         * Card details (for new card payments)
         */
        @Valid
        private CardDetailsDto cardDetails;
        
        /**
         * PayPal details (for PayPal payments)
         */
        @Valid
        private PayPalDetailsDto paypalDetails;
        
        /**
         * Bank transfer details (for bank payments)
         */
        @Valid
        private BankTransferDetailsDto bankTransferDetails;
        
        /**
         * Whether to authorize only (true) or capture immediately (false)
         */
        @Builder.Default
        private Boolean authorizeOnly = false;
        
        /**
         * 3D Secure authentication data
         */
        @Valid
        private ThreeDSecureDto threeDSecure;
    }
    
    /**
     * Card Details DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CardDetailsDto {
        
        /**
         * Cardholder name
         */
        @NotBlank(message = "Cardholder name is required")
        @Size(max = 100, message = "Cardholder name must not exceed 100 characters")
        private String cardholderName;
        
        /**
         * Card number (will be tokenized)
         */
        @NotBlank(message = "Card number is required")
        @Pattern(regexp = "^[0-9]{13,19}$", message = "Invalid card number format")
        private String cardNumber;
        
        /**
         * Expiry month (1-12)
         */
        @NotNull(message = "Expiry month is required")
        @Min(value = 1, message = "Expiry month must be between 1 and 12")
        @Max(value = 12, message = "Expiry month must be between 1 and 12")
        private Integer expiryMonth;
        
        /**
         * Expiry year (YYYY)
         */
        @NotNull(message = "Expiry year is required")
        @Min(value = 2024, message = "Expiry year must be current year or later")
        private Integer expiryYear;
        
        /**
         * CVV/CVC code
         */
        @NotBlank(message = "CVV is required")
        @Pattern(regexp = "^[0-9]{3,4}$", message = "CVV must be 3 or 4 digits")
        private String cvv;
        
        /**
         * Card brand (VISA, MASTERCARD, AMEX, etc.)
         */
        @Size(max = 20, message = "Card brand must not exceed 20 characters")
        private String cardBrand;
    }
    
    /**
     * PayPal Details DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PayPalDetailsDto {
        
        /**
         * PayPal email address
         */
        @NotBlank(message = "PayPal email is required")
        @Email(message = "Invalid PayPal email format")
        @Size(max = 255, message = "PayPal email must not exceed 255 characters")
        private String email;
        
        /**
         * PayPal payer ID
         */
        @Size(max = 100, message = "PayPal payer ID must not exceed 100 characters")
        private String payerId;
        
        /**
         * PayPal payment ID
         */
        @Size(max = 100, message = "PayPal payment ID must not exceed 100 characters")
        private String paymentId;
    }
    
    /**
     * Bank Transfer Details DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BankTransferDetailsDto {
        
        /**
         * Bank name
         */
        @NotBlank(message = "Bank name is required")
        @Size(max = 100, message = "Bank name must not exceed 100 characters")
        private String bankName;
        
        /**
         * Account holder name
         */
        @NotBlank(message = "Account holder name is required")
        @Size(max = 100, message = "Account holder name must not exceed 100 characters")
        private String accountHolderName;
        
        /**
         * Account number (masked)
         */
        @NotBlank(message = "Account number is required")
        @Size(max = 50, message = "Account number must not exceed 50 characters")
        private String accountNumber;
        
        /**
         * Routing number
         */
        @Size(max = 20, message = "Routing number must not exceed 20 characters")
        private String routingNumber;
        
        /**
         * IBAN (for international transfers)
         */
        @Size(max = 34, message = "IBAN must not exceed 34 characters")
        private String iban;
        
        /**
         * SWIFT code (for international transfers)
         */
        @Size(max = 11, message = "SWIFT code must not exceed 11 characters")
        private String swiftCode;
    }
    
    /**
     * 3D Secure DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ThreeDSecureDto {
        
        /**
         * 3D Secure version (1.0, 2.0, 2.1, 2.2)
         */
        @Size(max = 10, message = "3D Secure version must not exceed 10 characters")
        private String version;
        
        /**
         * Authentication response
         */
        @Size(max = 1, message = "Authentication response must be 1 character")
        private String authenticationResponse;
        
        /**
         * Transaction ID
         */
        @Size(max = 100, message = "Transaction ID must not exceed 100 characters")
        private String transactionId;
        
        /**
         * Cavv (Cardholder Authentication Verification Value)
         */
        @Size(max = 100, message = "CAVV must not exceed 100 characters")
        private String cavv;
        
        /**
         * ECI (Electronic Commerce Indicator)
         */
        @Size(max = 2, message = "ECI must not exceed 2 characters")
        private String eci;
    }
    
    /**
     * Validate that billing address is provided if different from shipping
     */
    public boolean isBillingSameAsShipping() {
        return billingAddress == null;
    }
    
    /**
     * Get effective billing address (shipping address if billing is null)
     */
    public Address getEffectiveBillingAddress() {
        return billingAddress != null ? billingAddress : shippingAddress;
    }
    
    /**
     * Check if this is an international order
     */
    public boolean isInternationalOrder(String businessCountry) {
        return shippingAddress != null && 
               !shippingAddress.getCountry().equalsIgnoreCase(businessCountry);
    }
    
    /**
     * Check if payment method requires additional verification
     */
    public boolean requiresPaymentVerification() {
        return paymentMethod != null && 
               (paymentMethod.getThreeDSecure() != null || 
                "CREDIT_CARD".equals(paymentMethod.getType()));
    }
}
