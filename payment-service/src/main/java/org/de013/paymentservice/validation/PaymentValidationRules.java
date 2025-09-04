package org.de013.paymentservice.validation;

import lombok.extern.slf4j.Slf4j;
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.entity.PaymentMethod;
import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.de013.paymentservice.entity.enums.PaymentMethodType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Payment validation rules and business logic validation.
 */
@Slf4j
@Component
public class PaymentValidationRules {
    
    /**
     * Validates a payment for processing.
     */
    public ValidationResult validatePaymentForProcessing(Payment payment) {
        List<String> errors = new ArrayList<>();
        
        // Basic payment validation
        if (payment == null) {
            errors.add("Payment cannot be null");
            return ValidationResult.invalid(errors);
        }
        
        // Amount validation
        if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Payment amount must be positive");
        }
        
        // Currency validation
        if (payment.getCurrency() == null) {
            errors.add("Payment currency is required");
        }
        
        // User validation
        if (payment.getUserId() == null) {
            errors.add("User ID is required");
        }
        
        // Order validation
        if (payment.getOrderId() == null) {
            errors.add("Order ID is required");
        }
        
        // Status validation
        if (payment.getStatus() != PaymentStatus.PENDING) {
            errors.add("Payment must be in PENDING status for processing");
        }
        
        return errors.isEmpty() ? ValidationResult.valid() : ValidationResult.invalid(errors);
    }
    
    /**
     * Validates a payment method for use in payment.
     */
    public ValidationResult validatePaymentMethodForPayment(PaymentMethod paymentMethod) {
        List<String> errors = new ArrayList<>();
        
        if (paymentMethod == null) {
            errors.add("Payment method cannot be null");
            return ValidationResult.invalid(errors);
        }
        
        // Active status validation
        if (!paymentMethod.getIsActive()) {
            errors.add("Payment method is not active");
        }
        
        // Expiration validation
        if (paymentMethod.isExpired()) {
            errors.add("Payment method has expired");
        }
        
        // Type-specific validation
        if (paymentMethod.getType() == PaymentMethodType.CARD) {
            if (paymentMethod.getMaskedCardNumber() == null || paymentMethod.getMaskedCardNumber().trim().isEmpty()) {
                errors.add("Card information is required");
            }

            if (paymentMethod.getCardBrand() == null || paymentMethod.getCardBrand().trim().isEmpty()) {
                errors.add("Card brand is required");
            }

            // Check if card is expired
            if (paymentMethod.isExpired()) {
                errors.add("Card has expired");
            }
        }
        
        // Stripe validation
        if (paymentMethod.getStripePaymentMethodId() == null || paymentMethod.getStripePaymentMethodId().trim().isEmpty()) {
            errors.add("Stripe payment method ID is required");
        }
        
        return errors.isEmpty() ? ValidationResult.valid() : ValidationResult.invalid(errors);
    }
    
    /**
     * Validates payment status transition.
     */
    public ValidationResult validateStatusTransition(PaymentStatus currentStatus, PaymentStatus newStatus) {
        List<String> errors = new ArrayList<>();
        
        if (currentStatus == null || newStatus == null) {
            errors.add("Payment status cannot be null");
            return ValidationResult.invalid(errors);
        }
        
        // Define valid transitions
        boolean isValidTransition = switch (currentStatus) {
            case PENDING -> newStatus == PaymentStatus.REQUIRES_ACTION ||
                          newStatus == PaymentStatus.REQUIRES_CONFIRMATION ||
                          newStatus == PaymentStatus.REQUIRES_PAYMENT_METHOD ||
                          newStatus == PaymentStatus.PROCESSING ||
                          newStatus == PaymentStatus.SUCCEEDED ||
                          newStatus == PaymentStatus.FAILED ||
                          newStatus == PaymentStatus.CANCELED;
                          
            case REQUIRES_ACTION -> newStatus == PaymentStatus.REQUIRES_CONFIRMATION ||
                                  newStatus == PaymentStatus.PROCESSING ||
                                  newStatus == PaymentStatus.SUCCEEDED ||
                                  newStatus == PaymentStatus.FAILED ||
                                  newStatus == PaymentStatus.CANCELED;
                                  
            case REQUIRES_CONFIRMATION -> newStatus == PaymentStatus.PROCESSING ||
                                        newStatus == PaymentStatus.SUCCEEDED ||
                                        newStatus == PaymentStatus.FAILED ||
                                        newStatus == PaymentStatus.CANCELED;
                                        
            case REQUIRES_PAYMENT_METHOD -> newStatus == PaymentStatus.PENDING ||
                                          newStatus == PaymentStatus.CANCELED;
                                          
            case PROCESSING -> newStatus == PaymentStatus.SUCCEEDED ||
                             newStatus == PaymentStatus.FAILED;
                             
            case SUCCEEDED, FAILED, CANCELED -> false; // Terminal states
        };
        
        if (!isValidTransition) {
            errors.add(String.format("Invalid status transition from %s to %s", currentStatus, newStatus));
        }
        
        return errors.isEmpty() ? ValidationResult.valid() : ValidationResult.invalid(errors);
    }
    
    /**
     * Validates refund amount against payment.
     */
    public ValidationResult validateRefundAmount(Payment payment, BigDecimal refundAmount, BigDecimal totalRefunded) {
        List<String> errors = new ArrayList<>();
        
        if (payment == null || refundAmount == null) {
            errors.add("Payment and refund amount cannot be null");
            return ValidationResult.invalid(errors);
        }
        
        // Positive amount check
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Refund amount must be positive");
        }
        
        // Check if payment is refundable
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            errors.add("Only succeeded payments can be refunded");
        }
        
        // Check refund amount doesn't exceed payment amount
        BigDecimal totalRefundAmount = (totalRefunded != null ? totalRefunded : BigDecimal.ZERO).add(refundAmount);
        if (totalRefundAmount.compareTo(payment.getAmount()) > 0) {
            errors.add("Total refund amount cannot exceed payment amount");
        }
        
        return errors.isEmpty() ? ValidationResult.valid() : ValidationResult.invalid(errors);
    }
    
    /**
     * Validates payment timeout.
     */
    public ValidationResult validatePaymentTimeout(Payment payment, int timeoutMinutes) {
        List<String> errors = new ArrayList<>();
        
        if (payment == null) {
            errors.add("Payment cannot be null");
            return ValidationResult.invalid(errors);
        }
        
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
        
        if (payment.getCreatedAt().isBefore(timeoutThreshold) && 
            (payment.getStatus() == PaymentStatus.PENDING || 
             payment.getStatus() == PaymentStatus.REQUIRES_ACTION ||
             payment.getStatus() == PaymentStatus.REQUIRES_CONFIRMATION)) {
            errors.add("Payment has timed out");
        }
        
        return errors.isEmpty() ? ValidationResult.valid() : ValidationResult.invalid(errors);
    }
    
    /**
     * Validation result wrapper.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        
        private ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
        }
        
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult invalid(List<String> errors) {
            return new ValidationResult(false, errors);
        }
        
        public static ValidationResult invalid(String error) {
            return new ValidationResult(false, List.of(error));
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }
        
        public String getErrorMessage() {
            return String.join(", ", errors);
        }
    }
}
