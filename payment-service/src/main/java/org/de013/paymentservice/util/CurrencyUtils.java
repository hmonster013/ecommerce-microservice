package org.de013.paymentservice.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for currency-related operations.
 */
@Slf4j
@Component
public class CurrencyUtils {
    
    // Supported currencies with their properties
    private static final Map<String, CurrencyInfo> SUPPORTED_CURRENCIES = Map.of(
        "USD", new CurrencyInfo("USD", "US Dollar", "$", 2, 50L, 99999999L),
        "EUR", new CurrencyInfo("EUR", "Euro", "€", 2, 50L, 99999999L),
        "GBP", new CurrencyInfo("GBP", "British Pound", "£", 2, 30L, 99999999L),
        "CAD", new CurrencyInfo("CAD", "Canadian Dollar", "C$", 2, 50L, 99999999L),
        "AUD", new CurrencyInfo("AUD", "Australian Dollar", "A$", 2, 50L, 99999999L),
        "JPY", new CurrencyInfo("JPY", "Japanese Yen", "¥", 0, 50L, 9999999L)
    );
    
    // Zero-decimal currencies (amounts in smallest unit)
    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of(
        "JPY", "KRW", "VND", "CLP", "ISK", "UGX", "PYG", "RWF", "XAF", "XOF", "XPF"
    );
    
    /**
     * Checks if a currency is supported.
     */
    public static boolean isSupportedCurrency(String currency) {
        return currency != null && SUPPORTED_CURRENCIES.containsKey(currency.toUpperCase());
    }
    
    /**
     * Gets all supported currency codes.
     */
    public static Set<String> getSupportedCurrencies() {
        return SUPPORTED_CURRENCIES.keySet();
    }
    
    /**
     * Gets currency information.
     */
    public static CurrencyInfo getCurrencyInfo(String currency) {
        return SUPPORTED_CURRENCIES.get(currency != null ? currency.toUpperCase() : null);
    }
    
    /**
     * Converts amount to smallest currency unit (cents for USD, yen for JPY).
     */
    public static Long toSmallestUnit(BigDecimal amount, String currency) {
        if (amount == null || currency == null) {
            return null;
        }
        
        if (isZeroDecimalCurrency(currency)) {
            // For zero-decimal currencies, amount is already in smallest unit
            return amount.setScale(0, RoundingMode.HALF_UP).longValue();
        } else {
            // For decimal currencies, multiply by 100 to get cents
            return amount.multiply(new BigDecimal("100"))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();
        }
    }
    
    /**
     * Converts amount from smallest currency unit to major unit.
     */
    public static BigDecimal fromSmallestUnit(Long amount, String currency) {
        if (amount == null || currency == null) {
            return null;
        }
        
        if (isZeroDecimalCurrency(currency)) {
            // For zero-decimal currencies, amount is already in major unit
            return new BigDecimal(amount);
        } else {
            // For decimal currencies, divide by 100 to get dollars
            return new BigDecimal(amount)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }
    }
    
    /**
     * Checks if currency is zero-decimal (no fractional units).
     */
    public static boolean isZeroDecimalCurrency(String currency) {
        return currency != null && ZERO_DECIMAL_CURRENCIES.contains(currency.toUpperCase());
    }
    
    /**
     * Gets the number of decimal places for a currency.
     */
    public static int getDecimalPlaces(String currency) {
        if (currency == null) {
            return 2; // Default
        }
        
        CurrencyInfo info = SUPPORTED_CURRENCIES.get(currency.toUpperCase());
        if (info != null) {
            return info.decimalPlaces();
        }
        
        return isZeroDecimalCurrency(currency) ? 0 : 2;
    }
    
    /**
     * Formats amount with proper decimal places for currency.
     */
    public static String formatAmount(BigDecimal amount, String currency) {
        if (amount == null) {
            return "0";
        }
        
        int decimalPlaces = getDecimalPlaces(currency);
        String symbol = getCurrencySymbol(currency);
        
        if (decimalPlaces == 0) {
            return String.format("%s%,.0f", symbol, amount);
        } else {
            return String.format("%s%,." + decimalPlaces + "f", symbol, amount);
        }
    }
    
    /**
     * Gets currency symbol.
     */
    public static String getCurrencySymbol(String currency) {
        if (currency == null) {
            return "$";
        }
        
        CurrencyInfo info = SUPPORTED_CURRENCIES.get(currency.toUpperCase());
        return info != null ? info.symbol() : currency.toUpperCase() + " ";
    }
    
    /**
     * Gets minimum amount for a currency.
     */
    public static Long getMinimumAmount(String currency) {
        CurrencyInfo info = getCurrencyInfo(currency);
        return info != null ? info.minimumAmount() : 50L; // Default 50 cents
    }
    
    /**
     * Gets maximum amount for a currency.
     */
    public static Long getMaximumAmount(String currency) {
        CurrencyInfo info = getCurrencyInfo(currency);
        return info != null ? info.maximumAmount() : 99999999L; // Default $999,999.99
    }
    
    /**
     * Validates amount for currency constraints.
     */
    public static boolean isValidAmount(BigDecimal amount, String currency) {
        if (amount == null || currency == null) {
            return false;
        }
        
        // Check if amount is positive
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        // Check decimal places
        if (amount.scale() > getDecimalPlaces(currency)) {
            return false;
        }
        
        // Convert to smallest unit for min/max validation
        Long amountInSmallestUnit = toSmallestUnit(amount, currency);
        if (amountInSmallestUnit == null) {
            return false;
        }
        
        // Check minimum and maximum amounts
        return amountInSmallestUnit >= getMinimumAmount(currency) &&
               amountInSmallestUnit <= getMaximumAmount(currency);
    }
    
    /**
     * Rounds amount to proper decimal places for currency.
     */
    public static BigDecimal roundAmount(BigDecimal amount, String currency) {
        if (amount == null) {
            return null;
        }
        
        int decimalPlaces = getDecimalPlaces(currency);
        return amount.setScale(decimalPlaces, RoundingMode.HALF_UP);
    }
    
    /**
     * Validates currency code format and existence.
     */
    public static boolean isValidCurrencyCode(String currency) {
        if (currency == null || currency.length() != 3) {
            return false;
        }
        
        try {
            Currency.getInstance(currency.toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * Gets currency display name.
     */
    public static String getCurrencyDisplayName(String currency) {
        if (currency == null) {
            return "Unknown Currency";
        }
        
        CurrencyInfo info = SUPPORTED_CURRENCIES.get(currency.toUpperCase());
        if (info != null) {
            return info.displayName();
        }
        
        try {
            Currency curr = Currency.getInstance(currency.toUpperCase());
            return curr.getDisplayName();
        } catch (IllegalArgumentException e) {
            return currency.toUpperCase();
        }
    }
    
    /**
     * Currency information record.
     */
    public record CurrencyInfo(
            String code,
            String displayName,
            String symbol,
            int decimalPlaces,
            long minimumAmount,
            long maximumAmount
    ) {}
}
