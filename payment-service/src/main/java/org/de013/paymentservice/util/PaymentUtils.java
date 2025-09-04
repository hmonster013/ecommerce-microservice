package org.de013.paymentservice.util;

import lombok.extern.slf4j.Slf4j;
import org.de013.paymentservice.entity.Payment;
import org.de013.paymentservice.entity.PaymentMethod;
import org.de013.paymentservice.entity.enums.PaymentStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for payment-related operations.
 */
@Slf4j
@Component
public class PaymentUtils {
    
    private static final DateTimeFormatter PAYMENT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    
    /**
     * Converts amount from dollars to cents.
     */
    public static Long dollarsToCents(BigDecimal dollars) {
        if (dollars == null) {
            return null;
        }
        return dollars.multiply(new BigDecimal("100"))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }
    
    /**
     * Converts amount from cents to dollars.
     */
    public static BigDecimal centsToDollars(Long cents) {
        if (cents == null) {
            return null;
        }
        return new BigDecimal(cents)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }
    
    /**
     * Formats amount for display with currency symbol.
     */
    public static String formatAmount(BigDecimal amount, String currency) {
        if (amount == null) {
            return "N/A";
        }
        
        String symbol = getCurrencySymbol(currency);
        return String.format("%s%.2f", symbol, amount);
    }
    
    /**
     * Gets currency symbol for common currencies.
     */
    public static String getCurrencySymbol(String currency) {
        if (currency == null) {
            return "$";
        }
        
        return switch (currency.toUpperCase()) {
            case "USD" -> "$";
            case "EUR" -> "€";
            case "GBP" -> "£";
            case "JPY" -> "¥";
            case "CAD" -> "C$";
            case "AUD" -> "A$";
            default -> currency + " ";
        };
    }
    
    /**
     * Checks if a payment is in a terminal state.
     */
    public static boolean isTerminalStatus(PaymentStatus status) {
        return status == PaymentStatus.SUCCEEDED ||
               status == PaymentStatus.FAILED ||
               status == PaymentStatus.CANCELED;
    }
    
    /**
     * Checks if a payment is in a pending state.
     */
    public static boolean isPendingStatus(PaymentStatus status) {
        return status == PaymentStatus.PENDING ||
               status == PaymentStatus.REQUIRES_ACTION ||
               status == PaymentStatus.REQUIRES_CONFIRMATION ||
               status == PaymentStatus.REQUIRES_PAYMENT_METHOD ||
               status == PaymentStatus.PROCESSING;
    }
    
    /**
     * Checks if a payment can be canceled.
     */
    public static boolean canBeCanceled(Payment payment) {
        return payment != null && 
               !isTerminalStatus(payment.getStatus()) &&
               payment.getStatus() != PaymentStatus.PROCESSING;
    }
    
    /**
     * Checks if a payment can be captured.
     */
    public static boolean canBeCaptured(Payment payment) {
        return payment != null && 
               payment.getStatus() == PaymentStatus.REQUIRES_CONFIRMATION;
    }
    
    /**
     * Checks if a payment can be refunded.
     */
    public static boolean canBeRefunded(Payment payment) {
        return payment != null && 
               payment.getStatus() == PaymentStatus.SUCCEEDED;
    }
    
    /**
     * Calculates the remaining refundable amount.
     */
    public static BigDecimal calculateRefundableAmount(Payment payment, BigDecimal totalRefunded) {
        if (payment == null || payment.getAmount() == null) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal refunded = totalRefunded != null ? totalRefunded : BigDecimal.ZERO;
        BigDecimal remaining = payment.getAmount().subtract(refunded);
        
        return remaining.max(BigDecimal.ZERO);
    }
    
    /**
     * Masks payment method details for security.
     */
    public static String maskPaymentMethod(PaymentMethod paymentMethod) {
        if (paymentMethod == null) {
            return "Unknown";
        }

        return switch (paymentMethod.getType()) {
            case CARD -> {
                String brand = paymentMethod.getCardBrand() != null ?
                    paymentMethod.getCardBrand().toUpperCase() : "Card";
                String maskedNumber = paymentMethod.getMaskedCardNumber();
                if (maskedNumber != null) {
                    yield String.format("%s %s", brand, maskedNumber);
                } else {
                    yield brand;
                }
            }
            case BANK_ACCOUNT -> "Bank Account";
            case WALLET -> {
                String walletType = paymentMethod.getWalletType();
                if (walletType != null) {
                    yield walletType.replace("_", " ").toUpperCase();
                } else {
                    yield "Digital Wallet";
                }
            }
            default -> paymentMethod.getType().toString();
        };
    }
    
    /**
     * Generates a payment description.
     */
    public static String generatePaymentDescription(Payment payment) {
        if (payment == null) {
            return "Payment";
        }

        String currencyCode = payment.getCurrency() != null ? payment.getCurrency().name() : "USD";
        return String.format("Payment for Order #%d - %s",
                payment.getOrderId(),
                formatAmount(payment.getAmount(), currencyCode));
    }
    
    /**
     * Calculates payment processing fee (example: 2.9% + $0.30).
     */
    public static BigDecimal calculateProcessingFee(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        
        // 2.9% + $0.30 fee structure
        BigDecimal percentageFee = amount.multiply(new BigDecimal("0.029"));
        BigDecimal fixedFee = new BigDecimal("0.30");
        
        return percentageFee.add(fixedFee).setScale(2, RoundingMode.HALF_UP);
    }
    
    /**
     * Groups payments by status.
     */
    public static Map<PaymentStatus, List<Payment>> groupByStatus(List<Payment> payments) {
        if (payments == null) {
            return Map.of();
        }
        
        return payments.stream()
                .collect(Collectors.groupingBy(Payment::getStatus));
    }
    
    /**
     * Calculates total amount for a list of payments.
     */
    public static BigDecimal calculateTotalAmount(List<Payment> payments) {
        if (payments == null || payments.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        return payments.stream()
                .filter(p -> p.getAmount() != null)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Checks if payment is expired based on creation time.
     */
    public static boolean isPaymentExpired(Payment payment, int expirationMinutes) {
        if (payment == null || payment.getCreatedAt() == null) {
            return false;
        }
        
        LocalDateTime expirationTime = payment.getCreatedAt().plusMinutes(expirationMinutes);
        return LocalDateTime.now().isAfter(expirationTime);
    }
    
    /**
     * Generates a payment reference for external systems.
     */
    public static String generatePaymentReference(Payment payment) {
        if (payment == null || payment.getId() == null) {
            return null;
        }
        
        String dateStr = payment.getCreatedAt() != null ? 
                payment.getCreatedAt().format(PAYMENT_DATE_FORMAT) : 
                LocalDateTime.now().format(PAYMENT_DATE_FORMAT);
        
        return String.format("PAY-%s-%06d", dateStr, payment.getId());
    }
    
    /**
     * Validates currency code format.
     */
    public static boolean isValidCurrency(String currency) {
        if (currency == null || currency.length() != 3) {
            return false;
        }
        
        // Basic validation - should be 3 uppercase letters
        return currency.matches("^[A-Z]{3}$");
    }
    
    /**
     * Normalizes currency code to uppercase.
     */
    public static String normalizeCurrency(String currency) {
        return currency != null ? currency.toUpperCase().trim() : null;
    }
}
