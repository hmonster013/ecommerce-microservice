package org.de013.orderservice.entity.valueobject;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

/**
 * Money Value Object
 * 
 * Represents a monetary amount with currency information.
 * This value object ensures proper handling of monetary calculations
 * and currency conversions with precision.
 * 
 * @author Development Team
 * @version 1.0.0
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Money implements Serializable, Comparable<Money> {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * The monetary amount
     */
    @Column(name = "amount", precision = 19, scale = 4)
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.00", message = "Amount must be non-negative")
    @Digits(integer = 15, fraction = 4, message = "Amount must have at most 15 integer digits and 4 decimal places")
    private BigDecimal amount;
    
    /**
     * The currency code (ISO 4217)
     */
    @Column(name = "currency", length = 3)
    @NotBlank(message = "Currency is required")
    private String currency;
    
    /**
     * Create Money with amount and currency
     *
     * @param amount the monetary amount
     * @param currency the currency code
     * @return Money instance
     */
    @JsonIgnore
    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }
    
    /**
     * Create Money with double amount and currency
     * 
     * @param amount the monetary amount
     * @param currency the currency code
     * @return Money instance
     */
    @JsonIgnore
    public static Money of(double amount, String currency) {
        return new Money(BigDecimal.valueOf(amount), currency);
    }

    /**
     * Create Money with string amount and currency
     *
     * @param amount the monetary amount as string
     * @param currency the currency code
     * @return Money instance
     */
    @JsonIgnore
    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    /**
     * Create zero Money with specified currency
     *
     * @param currency the currency code
     * @return Money instance with zero amount
     */
    @JsonIgnore
    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }
    
    /**
     * Add another Money amount (must be same currency)
     * 
     * @param other the other Money to add
     * @return new Money with sum
     * @throws IllegalArgumentException if currencies don't match
     */
    public Money add(Money other) {
        validateSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }
    
    /**
     * Subtract another Money amount (must be same currency)
     * 
     * @param other the other Money to subtract
     * @return new Money with difference
     * @throws IllegalArgumentException if currencies don't match
     */
    public Money subtract(Money other) {
        validateSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }
    
    /**
     * Multiply by a factor
     * 
     * @param factor the multiplication factor
     * @return new Money with multiplied amount
     */
    public Money multiply(BigDecimal factor) {
        return new Money(this.amount.multiply(factor), this.currency);
    }
    
    /**
     * Multiply by a double factor
     * 
     * @param factor the multiplication factor
     * @return new Money with multiplied amount
     */
    public Money multiply(double factor) {
        return multiply(BigDecimal.valueOf(factor));
    }
    
    /**
     * Divide by a divisor
     * 
     * @param divisor the division divisor
     * @param roundingMode the rounding mode to use
     * @return new Money with divided amount
     */
    public Money divide(BigDecimal divisor, RoundingMode roundingMode) {
        return new Money(this.amount.divide(divisor, 4, roundingMode), this.currency);
    }
    
    /**
     * Divide by a double divisor
     * 
     * @param divisor the division divisor
     * @param roundingMode the rounding mode to use
     * @return new Money with divided amount
     */
    public Money divide(double divisor, RoundingMode roundingMode) {
        return divide(BigDecimal.valueOf(divisor), roundingMode);
    }
    
    /**
     * Calculate percentage of this amount
     * 
     * @param percentage the percentage (e.g., 10 for 10%)
     * @param roundingMode the rounding mode to use
     * @return new Money with percentage amount
     */
    public Money percentage(BigDecimal percentage, RoundingMode roundingMode) {
        BigDecimal factor = percentage.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        return new Money(this.amount.multiply(factor).setScale(4, roundingMode), this.currency);
    }
    
    /**
     * Calculate percentage of this amount
     * 
     * @param percentage the percentage (e.g., 10 for 10%)
     * @return new Money with percentage amount (using HALF_UP rounding)
     */
    public Money percentage(double percentage) {
        return percentage(BigDecimal.valueOf(percentage), RoundingMode.HALF_UP);
    }
    
    /**
     * Round to specified decimal places
     * 
     * @param scale the number of decimal places
     * @param roundingMode the rounding mode to use
     * @return new Money with rounded amount
     */
    public Money round(int scale, RoundingMode roundingMode) {
        return new Money(this.amount.setScale(scale, roundingMode), this.currency);
    }
    
    /**
     * Round to currency's default decimal places
     * 
     * @return new Money with rounded amount
     */
    public Money roundToCurrencyScale() {
        try {
            Currency curr = Currency.getInstance(this.currency);
            int scale = curr.getDefaultFractionDigits();
            return round(scale, RoundingMode.HALF_UP);
        } catch (IllegalArgumentException e) {
            // Default to 2 decimal places if currency is invalid
            return round(2, RoundingMode.HALF_UP);
        }
    }
    
    /**
     * Check if this Money is zero
     *
     * @return true if amount is zero
     */
    @JsonIgnore
    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Check if this Money is positive
     *
     * @return true if amount is greater than zero
     */
    @JsonIgnore
    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Check if this Money is negative
     *
     * @return true if amount is less than zero
     */
    @JsonIgnore
    public boolean isNegative() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }
    
    /**
     * Get absolute value
     * 
     * @return new Money with absolute amount
     */
    public Money abs() {
        return new Money(amount.abs(), currency);
    }
    
    /**
     * Negate the amount
     * 
     * @return new Money with negated amount
     */
    public Money negate() {
        return new Money(amount.negate(), currency);
    }
    
    /**
     * Format as currency string for display
     * 
     * @return formatted currency string
     */
    public String format() {
        try {
            Currency curr = Currency.getInstance(currency);
            NumberFormat formatter = NumberFormat.getCurrencyInstance();
            formatter.setCurrency(curr);
            return formatter.format(amount);
        } catch (IllegalArgumentException e) {
            // Fallback formatting if currency is invalid
            return String.format("%s %.2f", currency, amount);
        }
    }
    
    /**
     * Format as currency string for specific locale
     * 
     * @param locale the locale to use for formatting
     * @return formatted currency string
     */
    public String format(Locale locale) {
        try {
            Currency curr = Currency.getInstance(currency);
            NumberFormat formatter = NumberFormat.getCurrencyInstance(locale);
            formatter.setCurrency(curr);
            return formatter.format(amount);
        } catch (IllegalArgumentException e) {
            // Fallback formatting if currency is invalid
            return String.format("%s %.2f", currency, amount);
        }
    }
    
    /**
     * Get the currency symbol
     * 
     * @return currency symbol
     */
    public String getCurrencySymbol() {
        try {
            Currency curr = Currency.getInstance(currency);
            return curr.getSymbol();
        } catch (IllegalArgumentException e) {
            return currency;
        }
    }
    
    /**
     * Get the currency display name
     * 
     * @return currency display name
     */
    public String getCurrencyDisplayName() {
        try {
            Currency curr = Currency.getInstance(currency);
            return curr.getDisplayName();
        } catch (IllegalArgumentException e) {
            return currency;
        }
    }
    
    /**
     * Validate that another Money has the same currency
     * 
     * @param other the other Money to validate
     * @throws IllegalArgumentException if currencies don't match
     */
    private void validateSameCurrency(Money other) {
        if (other == null) {
            throw new IllegalArgumentException("Other money cannot be null");
        }
        if (!Objects.equals(this.currency, other.currency)) {
            throw new IllegalArgumentException(
                String.format("Currency mismatch: %s vs %s", this.currency, other.currency));
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Money money = (Money) obj;
        return Objects.equals(amount, money.amount) && Objects.equals(currency, money.currency);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(amount, currency);
    }
    
    @Override
    public int compareTo(Money other) {
        validateSameCurrency(other);
        return this.amount.compareTo(other.amount);
    }
    
    /**
     * Check if this Money is greater than another
     * 
     * @param other the other Money to compare
     * @return true if this is greater than other
     */
    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }
    
    /**
     * Check if this Money is less than another
     * 
     * @param other the other Money to compare
     * @return true if this is less than other
     */
    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }
    
    /**
     * Check if this Money is greater than or equal to another
     * 
     * @param other the other Money to compare
     * @return true if this is greater than or equal to other
     */
    public boolean isGreaterThanOrEqual(Money other) {
        return compareTo(other) >= 0;
    }
    
    /**
     * Check if this Money is less than or equal to another
     * 
     * @param other the other Money to compare
     * @return true if this is less than or equal to other
     */
    public boolean isLessThanOrEqual(Money other) {
        return compareTo(other) <= 0;
    }
}
