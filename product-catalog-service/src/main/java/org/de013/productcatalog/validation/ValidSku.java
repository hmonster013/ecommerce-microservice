package org.de013.productcatalog.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Custom validation annotation for SKU format validation.
 * SKU must follow the pattern: 3-4 uppercase letters followed by 4-6 digits
 * Examples: ABC1234, PROD123456, SKU9999
 */
@Documented
@Constraint(validatedBy = SkuValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidSku {

    String message() default "SKU must be 3-4 uppercase letters followed by 4-6 digits (e.g., ABC1234)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Allow custom pattern for specific use cases
     */
    String pattern() default "^[A-Z]{3,4}\\d{4,6}$";

    /**
     * Whether to allow null values
     */
    boolean allowNull() default false;
}
