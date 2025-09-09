package org.de013.userservice.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Password strength validator implementation
 * Validates password complexity, length, and security requirements
 */
public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {
    
    @Override
    public void initialize(ValidPassword constraintAnnotation) {
        // No initialization needed
    }
    
    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return false;
        }
        
        // Check basic length
        if (password.length() < 8) {
            addConstraintViolation(context, "password.tooShort");
            return false;
        }

        // Check maximum length
        if (password.length() > 128) {
            addConstraintViolation(context, "password.tooLong");
            return false;
        }

        // Check for at least one lowercase letter
        if (!password.matches(".*[a-z].*")) {
            addConstraintViolation(context, "password.noLowercase");
            return false;
        }

        // Check for at least one uppercase letter
        if (!password.matches(".*[A-Z].*")) {
            addConstraintViolation(context, "password.noUppercase");
            return false;
        }

        // Check for at least one digit
        if (!password.matches(".*\\d.*")) {
            addConstraintViolation(context, "password.noDigit");
            return false;
        }

        // Check for at least one special character
        if (!password.matches(".*[@$!%*?&].*")) {
            addConstraintViolation(context, "password.noSpecialChar");
            return false;
        }
        
        return true;
    }
    
    private void addConstraintViolation(ConstraintValidatorContext context, String messageKey) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("{" + messageKey + "}").addConstraintViolation();
    }
}
