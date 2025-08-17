package org.de013.userservice.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Custom validation annotation for name validation
 * Validates name format, length, and character restrictions
 */
@Documented
@Constraint(validatedBy = NameValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidName {
    
    String message() default "{firstName.invalid}";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Minimum length for the name
     */
    int min() default 2;
    
    /**
     * Maximum length for the name
     */
    int max() default 50;
    
    /**
     * Whether to allow special characters like apostrophes and hyphens
     */
    boolean allowSpecialChars() default true;
}
