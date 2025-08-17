package org.de013.productcatalog.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Validator implementation for @ValidPrice annotation.
 * Validates price according to business rules.
 */
@Slf4j
public class PriceValidator implements ConstraintValidator<ValidPrice, BigDecimal> {

    private double min;
    private double max;
    private boolean allowNull;
    private int maxDecimalPlaces;

    @Override
    public void initialize(ValidPrice constraintAnnotation) {
        this.min = constraintAnnotation.min();
        this.max = constraintAnnotation.max();
        this.allowNull = constraintAnnotation.allowNull();
        this.maxDecimalPlaces = constraintAnnotation.maxDecimalPlaces();
        
        log.debug("Initialized Price validator - min: {}, max: {}, allowNull: {}, maxDecimalPlaces: {}", 
                 min, max, allowNull, maxDecimalPlaces);
    }

    @Override
    public boolean isValid(BigDecimal price, ConstraintValidatorContext context) {
        // Handle null values
        if (price == null) {
            return allowNull;
        }

        // Check if price is positive
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            addCustomMessage(context, "Price must be greater than zero");
            return false;
        }

        // Check minimum value
        BigDecimal minPrice = BigDecimal.valueOf(min);
        if (price.compareTo(minPrice) < 0) {
            addCustomMessage(context, 
                String.format("Price must be at least %s", minPrice.toPlainString()));
            return false;
        }

        // Check maximum value
        BigDecimal maxPrice = BigDecimal.valueOf(max);
        if (price.compareTo(maxPrice) > 0) {
            addCustomMessage(context, 
                String.format("Price cannot exceed %s", maxPrice.toPlainString()));
            return false;
        }

        // Check decimal places
        if (getDecimalPlaces(price) > maxDecimalPlaces) {
            addCustomMessage(context, 
                String.format("Price cannot have more than %d decimal places", maxDecimalPlaces));
            return false;
        }

        // Business rule: Check for suspicious pricing patterns
        if (isSuspiciousPrice(price)) {
            addCustomMessage(context, "Price appears to be invalid or suspicious");
            return false;
        }

        log.debug("Price validation passed for: {}", price);
        return true;
    }

    /**
     * Get number of decimal places in a BigDecimal
     */
    private int getDecimalPlaces(BigDecimal value) {
        String string = value.stripTrailingZeros().toPlainString();
        int index = string.indexOf('.');
        return index < 0 ? 0 : string.length() - index - 1;
    }

    /**
     * Check for suspicious pricing patterns
     */
    private boolean isSuspiciousPrice(BigDecimal price) {
        // Check for prices with too many repeating digits (e.g., 111.11, 999.99)
        String priceStr = price.toPlainString().replace(".", "");
        if (priceStr.length() > 3) {
            char firstChar = priceStr.charAt(0);
            boolean allSame = priceStr.chars().allMatch(c -> c == firstChar);
            if (allSame) {
                return true;
            }
        }

        // Check for unrealistic precision (e.g., 19.999999)
        if (getDecimalPlaces(price) > 4) {
            return true;
        }

        return false;
    }

    /**
     * Add custom validation message
     */
    private void addCustomMessage(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
               .addConstraintViolation();
    }
}
