package org.de013.productcatalog.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * Validator implementation for @ValidSku annotation.
 * Validates SKU format according to business rules.
 */
@Slf4j
public class SkuValidator implements ConstraintValidator<ValidSku, String> {

    private Pattern pattern;
    private boolean allowNull;

    @Override
    public void initialize(ValidSku constraintAnnotation) {
        this.pattern = Pattern.compile(constraintAnnotation.pattern());
        this.allowNull = constraintAnnotation.allowNull();
        log.debug("Initialized SKU validator with pattern: {} and allowNull: {}", 
                 constraintAnnotation.pattern(), allowNull);
    }

    @Override
    public boolean isValid(String sku, ConstraintValidatorContext context) {
        // Handle null values
        if (sku == null) {
            return allowNull;
        }

        // Handle empty strings
        if (sku.trim().isEmpty()) {
            addCustomMessage(context, "{ValidSku.empty}");
            return false;
        }

        // Validate pattern
        if (!pattern.matcher(sku).matches()) {
            addCustomMessage(context, "{ValidSku.invalid.format}");
            return false;
        }

        // Additional business rules
        if (sku.length() < 7 || sku.length() > 10) {
            addCustomMessage(context, "{ValidSku.invalid.length}");
            return false;
        }

        // Check for reserved prefixes (business rule)
        if (isReservedPrefix(sku)) {
            addCustomMessage(context, "{ValidSku.reserved.prefix}");
            return false;
        }

        log.debug("SKU validation passed for: {}", sku);
        return true;
    }

    /**
     * Check if SKU uses reserved prefixes
     */
    private boolean isReservedPrefix(String sku) {
        String[] reservedPrefixes = {"SYS", "ADM", "TST", "DEV"};
        String prefix = sku.substring(0, 3);
        
        for (String reserved : reservedPrefixes) {
            if (reserved.equals(prefix)) {
                return true;
            }
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
