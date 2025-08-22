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
    @NotNull(message = "{user.id.required}")
    @Positive(message = "{user.id.positive}")
    private Long userId;
    
    /**
     * ID of the shopping cart to convert to order
     */
    @NotNull(message = "{cart.id.required}")
    @Positive(message = "{cart.id.positive}")
    private Long cartId;
    
    /**
     * Type of order being placed
     */
    @NotNull(message = "{order.type.required}")
    private OrderType orderType;
    
    /**
     * Shipping address for the order
     */
    @Valid
    @NotNull(message = "{shipping.address.required}")
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
    @NotNull(message = "{payment.method.required}")
    private PaymentMethodDto paymentMethod;
    
    /**
     * Shipping method preference
     */
    @NotBlank(message = "{shipping.method.required}")
    @Size(max = 50, message = "{field.size.max}")
    private String shippingMethod;
    
    /**
     * Currency for the order
     */
    @NotBlank(message = "{currency.required}")
    @Size(min = 3, max = 3, message = "{currency.size}")
    @Pattern(regexp = "^[A-Z]{3}$", message = "{currency.format}")
    private String currency;
    
    /**
     * Customer notes for the order
     */
    @Size(max = 2000, message = "{customer.notes.size}")
    private String customerNotes;
    
    /**
     * Whether this order is a gift
     */
    @Builder.Default
    private Boolean isGift = false;
    
    /**
     * Gift message if this is a gift order
     */
    @Size(max = 1000, message = "{gift.message.size}")
    private String giftMessage;
    
    /**
     * Preferred delivery date
     */
    private LocalDateTime preferredDeliveryDate;
    
    /**
     * Special delivery instructions
     */
    @Size(max = 1000, message = "{delivery.instructions.size}")
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
    @DecimalMin(value = "0.0", message = "{insurance.value.non-negative}")
    private BigDecimal insuranceValue;
    
    /**
     * Promotional code to apply
     */
    @Size(max = 50, message = "{promo.code.size}")
    private String promoCode;
    
    /**
     * Source of the order (WEB, MOBILE, API, etc.)
     */
    @Size(max = 20, message = "{order.source.size}")
    @Builder.Default
    private String orderSource = "WEB";
    
    /**
     * Customer IP address for fraud detection
     */
    @Size(max = 45, message = "{customer.ip.size}")
    private String customerIp;
    
    /**
     * Customer user agent for fraud detection
     */
    @Size(max = 1000, message = "{customer.user-agent.size}")
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
        @NotBlank(message = "{payment.type.required}")
        @Size(max = 50, message = "{payment.type.size}")
        private String type;
        
        /**
         * Payment token from payment gateway
         */
        @Size(max = 200, message = "{payment.token.size}")
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
        @NotBlank(message = "{card.holder.name.required}")
        @Size(max = 100, message = "{card.holder.name.size}")
        private String cardholderName;
        
        /**
         * Card number (will be tokenized)
         */
        @NotBlank(message = "{card.number.required}")
        @Pattern(regexp = "^[0-9]{13,19}$", message = "{card.number.format}")
        private String cardNumber;
        
        /**
         * Expiry month (1-12)
         */
        @NotNull(message = "{card.expiry.month.required}")
        @Min(value = 1, message = "{card.expiry.month.range}")
        @Max(value = 12, message = "{card.expiry.month.range}")
        private Integer expiryMonth;
        
        /**
         * Expiry year (YYYY)
         */
        @NotNull(message = "{card.expiry.year.required}")
        @Min(value = 2024, message = "{card.expiry.year.min}")
        private Integer expiryYear;
        
        /**
         * CVV/CVC code
         */
        @NotBlank(message = "{card.cvv.required}")
        @Pattern(regexp = "^[0-9]{3,4}$", message = "{card.cvv.format}")
        private String cvv;
        
        /**
         * Card brand (VISA, MASTERCARD, AMEX, etc.)
         */
        @Size(max = 20, message = "{card.brand.size}")
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
        @NotBlank(message = "{paypal.email.required}")
        @Email(message = "{paypal.email.format}")
        @Size(max = 255, message = "{paypal.email.size}")
        private String email;
        
        /**
         * PayPal payer ID
         */
        @Size(max = 100, message = "{paypal.payer.id.size}")
        private String payerId;
        
        /**
         * PayPal payment ID
         */
        @Size(max = 100, message = "{paypal.payment.id.size}")
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
        @NotBlank(message = "{bank.name.required}")
        @Size(max = 100, message = "{bank.name.size}")
        private String bankName;
        
        /**
         * Account holder name
         */
        @NotBlank(message = "{bank.account.holder.required}")
        @Size(max = 100, message = "{bank.account.holder.size}")
        private String accountHolderName;
        
        /**
         * Account number (masked)
         */
        @NotBlank(message = "{bank.account.number.required}")
        @Size(max = 50, message = "{bank.account.number.size}")
        private String accountNumber;
        
        /**
         * Routing number
         */
        @Size(max = 20, message = "{bank.routing.number.size}")
        private String routingNumber;
        
        /**
         * IBAN (for international transfers)
         */
        @Size(max = 34, message = "{bank.iban.size}")
        private String iban;
        
        /**
         * SWIFT code (for international transfers)
         */
        @Size(max = 11, message = "{bank.swift.code.size}")
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
        @Size(max = 10, message = "{3ds.version.size}")
        private String version;
        
        /**
         * Authentication response
         */
        @Size(max = 1, message = "{3ds.auth.response.size}")
        private String authenticationResponse;
        
        /**
         * Transaction ID
         */
        @Size(max = 100, message = "{3ds.transaction.id.size}")
        private String transactionId;
        
        /**
         * Cavv (Cardholder Authentication Verification Value)
         */
        @Size(max = 100, message = "{3ds.cavv.size}")
        private String cavv;
        
        /**
         * ECI (Electronic Commerce Indicator)
         */
        @Size(max = 2, message = "{3ds.eci.size}")
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
