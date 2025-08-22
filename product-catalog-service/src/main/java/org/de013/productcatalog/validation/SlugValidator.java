package org.de013.productcatalog.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * Validator implementation for @ValidSlug annotation.
 * Validates URL slug format according to web standards.
 */
@Slf4j
public class SlugValidator implements ConstraintValidator<ValidSlug, String> {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final String[] RESERVED_SLUGS = {
        "admin", "api", "www", "mail", "ftp", "localhost", "root", "test", "staging", "prod", "production"
    };

    private int minLength;
    private int maxLength;
    private boolean allowNull;

    @Override
    public void initialize(ValidSlug constraintAnnotation) {
        this.minLength = constraintAnnotation.minLength();
        this.maxLength = constraintAnnotation.maxLength();
        this.allowNull = constraintAnnotation.allowNull();
        
        log.debug("Initialized Slug validator - minLength: {}, maxLength: {}, allowNull: {}", 
                 minLength, maxLength, allowNull);
    }

    @Override
    public boolean isValid(String slug, ConstraintValidatorContext context) {
        // Handle null values
        if (slug == null) {
            return allowNull;
        }

        // Handle empty strings
        if (slug.trim().isEmpty()) {
            addCustomMessage(context, "{ValidSlug.empty}");
            return false;
        }

        // Check length constraints
        if (slug.length() < minLength) {
            addCustomMessage(context, "{ValidSlug.too.short}");
            return false;
        }

        if (slug.length() > maxLength) {
            addCustomMessage(context, "{ValidSlug.too.long}");
            return false;
        }

        // Check pattern (lowercase letters, numbers, hyphens only)
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            addCustomMessage(context, "{ValidSlug.invalid.format}");
            return false;
        }

        // Check for reserved slugs
        if (isReservedSlug(slug)) {
            addCustomMessage(context, "{ValidSlug.reserved}");
            return false;
        }

        // Check for consecutive hyphens
        if (slug.contains("--")) {
            addCustomMessage(context, "{ValidSlug.consecutive.hyphens}");
            return false;
        }

        // Check for too many hyphens (business rule)
        long hyphenCount = slug.chars().filter(ch -> ch == '-').count();
        if (hyphenCount > 5) {
            addCustomMessage(context, "{ValidSlug.too.many.hyphens}");
            return false;
        }

        log.debug("Slug validation passed for: {}", slug);
        return true;
    }

    /**
     * Check if slug is reserved
     */
    private boolean isReservedSlug(String slug) {
        for (String reserved : RESERVED_SLUGS) {
            if (reserved.equals(slug)) {
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
