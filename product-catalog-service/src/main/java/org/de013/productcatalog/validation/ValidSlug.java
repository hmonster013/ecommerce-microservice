package org.de013.productcatalog.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Custom validation annotation for URL slug validation.
 * Slug must be URL-friendly: lowercase letters, numbers, and hyphens only
 * Examples: electronics, mobile-phones, gaming-laptops
 */
@Documented
@Constraint(validatedBy = SlugValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidSlug {

    String message() default "Slug must contain only lowercase letters, numbers, and hyphens";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Minimum length (default: 2)
     */
    int minLength() default 2;

    /**
     * Maximum length (default: 100)
     */
    int maxLength() default 100;

    /**
     * Whether to allow null values
     */
    boolean allowNull() default false;
}
