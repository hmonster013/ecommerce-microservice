package org.de013.userservice.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Custom validation annotation for password confirmation validation
 * Validates that password and confirmPassword fields match
 */
@Documented
@Constraint(validatedBy = PasswordConfirmationValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPasswordConfirmation {

    String message() default "{password.mismatch}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * Field name for the password field
     */
    String passwordField() default "newPassword";

    /**
     * Field name for the confirm password field
     */
    String confirmPasswordField() default "confirmPassword";
}
