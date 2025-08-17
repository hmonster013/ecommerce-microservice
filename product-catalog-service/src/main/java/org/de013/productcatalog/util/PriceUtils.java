package org.de013.productcatalog.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

/**
 * Utility class for price calculations and formatting.
 * Provides methods for price manipulation, formatting, and calculations.
 */
@Slf4j
@UtilityClass
public class PriceUtils {

    private static final int DEFAULT_SCALE = 2;
    private static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;
    private static final Currency DEFAULT_CURRENCY = Currency.getInstance("USD");

    /**
     * Format price for display with currency symbol.
     * 
     * @param price the price to format
     * @param locale the locale for formatting
     * @return formatted price string
     */
    public static String formatPrice(BigDecimal price, Locale locale) {
        if (price == null) {
            return "N/A";
        }

        try {
            NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(locale);
            return currencyFormat.format(price);
        } catch (Exception e) {
            log.warn("Error formatting price {} for locale {}: {}", price, locale, e.getMessage());
            return "$" + price.setScale(DEFAULT_SCALE, DEFAULT_ROUNDING).toPlainString();
        }
    }

    /**
     * Format price for display with default US locale.
     * 
     * @param price the price to format
     * @return formatted price string
     */
    public static String formatPrice(BigDecimal price) {
        return formatPrice(price, Locale.US);
    }

    /**
     * Calculate discount amount.
     * 
     * @param originalPrice the original price
     * @param discountPrice the discounted price
     * @return discount amount
     */
    public static BigDecimal calculateDiscountAmount(BigDecimal originalPrice, BigDecimal discountPrice) {
        if (originalPrice == null || discountPrice == null) {
            return BigDecimal.ZERO;
        }

        if (originalPrice.compareTo(discountPrice) <= 0) {
            return BigDecimal.ZERO;
        }

        return originalPrice.subtract(discountPrice).setScale(DEFAULT_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Calculate discount percentage.
     * 
     * @param originalPrice the original price
     * @param discountPrice the discounted price
     * @return discount percentage (0-100)
     */
    public static BigDecimal calculateDiscountPercentage(BigDecimal originalPrice, BigDecimal discountPrice) {
        if (originalPrice == null || discountPrice == null || originalPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        if (originalPrice.compareTo(discountPrice) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal discountAmount = originalPrice.subtract(discountPrice);
        BigDecimal percentage = discountAmount.divide(originalPrice, 4, DEFAULT_ROUNDING)
                                            .multiply(BigDecimal.valueOf(100))
                                            .setScale(DEFAULT_SCALE, DEFAULT_ROUNDING);

        return percentage;
    }

    /**
     * Apply discount percentage to price.
     * 
     * @param originalPrice the original price
     * @param discountPercentage the discount percentage (0-100)
     * @return discounted price
     */
    public static BigDecimal applyDiscountPercentage(BigDecimal originalPrice, BigDecimal discountPercentage) {
        if (originalPrice == null || discountPercentage == null) {
            return originalPrice;
        }

        if (discountPercentage.compareTo(BigDecimal.ZERO) <= 0) {
            return originalPrice;
        }

        if (discountPercentage.compareTo(BigDecimal.valueOf(100)) >= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal discountMultiplier = BigDecimal.ONE.subtract(
            discountPercentage.divide(BigDecimal.valueOf(100), 4, DEFAULT_ROUNDING)
        );

        return originalPrice.multiply(discountMultiplier).setScale(DEFAULT_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Calculate tax amount.
     * 
     * @param price the base price
     * @param taxRate the tax rate (e.g., 0.08 for 8%)
     * @return tax amount
     */
    public static BigDecimal calculateTax(BigDecimal price, BigDecimal taxRate) {
        if (price == null || taxRate == null) {
            return BigDecimal.ZERO;
        }

        return price.multiply(taxRate).setScale(DEFAULT_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Calculate price including tax.
     * 
     * @param price the base price
     * @param taxRate the tax rate (e.g., 0.08 for 8%)
     * @return price including tax
     */
    public static BigDecimal calculatePriceWithTax(BigDecimal price, BigDecimal taxRate) {
        if (price == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal tax = calculateTax(price, taxRate);
        return price.add(tax);
    }

    /**
     * Round price to standard currency precision.
     * 
     * @param price the price to round
     * @return rounded price
     */
    public static BigDecimal roundPrice(BigDecimal price) {
        if (price == null) {
            return BigDecimal.ZERO;
        }

        return price.setScale(DEFAULT_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Check if price is valid (positive and reasonable).
     * 
     * @param price the price to validate
     * @return true if price is valid
     */
    public static boolean isValidPrice(BigDecimal price) {
        if (price == null) {
            return false;
        }

        // Must be positive
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        // Must be reasonable (not too large)
        if (price.compareTo(BigDecimal.valueOf(1_000_000)) > 0) {
            return false;
        }

        // Check decimal places
        if (price.scale() > DEFAULT_SCALE) {
            return false;
        }

        return true;
    }

    /**
     * Compare prices with tolerance for floating point precision.
     * 
     * @param price1 first price
     * @param price2 second price
     * @param tolerance tolerance for comparison
     * @return true if prices are equal within tolerance
     */
    public static boolean pricesEqual(BigDecimal price1, BigDecimal price2, BigDecimal tolerance) {
        if (price1 == null && price2 == null) {
            return true;
        }

        if (price1 == null || price2 == null) {
            return false;
        }

        BigDecimal difference = price1.subtract(price2).abs();
        return difference.compareTo(tolerance) <= 0;
    }

    /**
     * Compare prices with default tolerance (0.01).
     * 
     * @param price1 first price
     * @param price2 second price
     * @return true if prices are equal within default tolerance
     */
    public static boolean pricesEqual(BigDecimal price1, BigDecimal price2) {
        return pricesEqual(price1, price2, BigDecimal.valueOf(0.01));
    }

    /**
     * Get the higher of two prices.
     * 
     * @param price1 first price
     * @param price2 second price
     * @return higher price
     */
    public static BigDecimal max(BigDecimal price1, BigDecimal price2) {
        if (price1 == null) return price2;
        if (price2 == null) return price1;
        return price1.compareTo(price2) >= 0 ? price1 : price2;
    }

    /**
     * Get the lower of two prices.
     * 
     * @param price1 first price
     * @param price2 second price
     * @return lower price
     */
    public static BigDecimal min(BigDecimal price1, BigDecimal price2) {
        if (price1 == null) return price2;
        if (price2 == null) return price1;
        return price1.compareTo(price2) <= 0 ? price1 : price2;
    }

    /**
     * Calculate average price from array of prices.
     * 
     * @param prices array of prices
     * @return average price
     */
    public static BigDecimal calculateAverage(BigDecimal... prices) {
        if (prices == null || prices.length == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;

        for (BigDecimal price : prices) {
            if (price != null) {
                sum = sum.add(price);
                count++;
            }
        }

        if (count == 0) {
            return BigDecimal.ZERO;
        }

        return sum.divide(BigDecimal.valueOf(count), DEFAULT_SCALE, DEFAULT_ROUNDING);
    }

    /**
     * Format price range for display.
     * 
     * @param minPrice minimum price
     * @param maxPrice maximum price
     * @param locale locale for formatting
     * @return formatted price range
     */
    public static String formatPriceRange(BigDecimal minPrice, BigDecimal maxPrice, Locale locale) {
        if (minPrice == null && maxPrice == null) {
            return "N/A";
        }

        if (minPrice == null) {
            return "Up to " + formatPrice(maxPrice, locale);
        }

        if (maxPrice == null) {
            return "From " + formatPrice(minPrice, locale);
        }

        if (pricesEqual(minPrice, maxPrice)) {
            return formatPrice(minPrice, locale);
        }

        return formatPrice(minPrice, locale) + " - " + formatPrice(maxPrice, locale);
    }

    /**
     * Format price range with default US locale.
     * 
     * @param minPrice minimum price
     * @param maxPrice maximum price
     * @return formatted price range
     */
    public static String formatPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        return formatPriceRange(minPrice, maxPrice, Locale.US);
    }
}
