package org.de013.productcatalog.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Custom validation annotation for price validation.
 * Validates price according to business rules:
 * - Must be positive
 * - Maximum 2 decimal places
 * - Within reasonable range
 */
@Documented
@Constraint(validatedBy = PriceValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPrice {

    String message() default "Price must be positive with maximum 2 decimal places";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Minimum allowed price (default: 0.01)
     */
    double min() default 0.01;

    /**
     * Maximum allowed price (default: 999999.99)
     */
    double max() default 999999.99;

    /**
     * Whether to allow null values
     */
    boolean allowNull() default false;

    /**
     * Maximum number of decimal places (default: 2)
     */
    int maxDecimalPlaces() default 2;
}
