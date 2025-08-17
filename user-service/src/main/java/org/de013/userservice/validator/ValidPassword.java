package org.de013.userservice.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Custom validation annotation for password strength validation
 * Validates password complexity including length, character types, and common patterns
 */
@Documented
@Constraint(validatedBy = PasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    String message() default "Password does not meet security requirements";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
