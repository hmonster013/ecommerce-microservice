package org.de013.userservice.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Custom validation annotation for phone number validation
 * Validates phone number format and length
 */
@Documented
@Constraint(validatedBy = PhoneValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPhone {
    
    String message() default "Invalid phone number format";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Whether to allow international format
     */
    boolean international() default true;
    
    /**
     * Country code for validation (e.g., "VN", "US")
     */
    String countryCode() default "";
}
