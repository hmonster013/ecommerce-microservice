package org.de013.paymentservice.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Custom validation annotation for payment amount validation.
 * Validates that an amount is positive, within limits, and has proper decimal places.
 */
@Documented
@Constraint(validatedBy = AmountValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidAmount {
    
    String message() default "Invalid amount";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Minimum allowed amount (in cents/smallest currency unit).
     */
    long min() default 50; // 50 cents minimum
    
    /**
     * Maximum allowed amount (in cents/smallest currency unit).
     */
    long max() default 99999999L; // $999,999.99 maximum
    
    /**
     * Currency code for validation.
     */
    String currency() default "USD";
    
    /**
     * Whether to allow zero amounts.
     */
    boolean allowZero() default false;
    
    /**
     * Whether to allow null values.
     */
    boolean allowNull() default false;
    
    /**
     * Maximum number of decimal places allowed.
     */
    int maxDecimalPlaces() default 2;
}
