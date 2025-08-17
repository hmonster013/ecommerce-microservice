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
            addConstraintViolation(context, "Password must be at least 8 characters long");
            return false;
        }
        
        // Check maximum length
        if (password.length() > 128) {
            addConstraintViolation(context, "Password must not exceed 128 characters");
            return false;
        }
        
        // Check for at least one lowercase letter
        if (!password.matches(".*[a-z].*")) {
            addConstraintViolation(context, "Password must contain at least one lowercase letter");
            return false;
        }
        
        // Check for at least one uppercase letter
        if (!password.matches(".*[A-Z].*")) {
            addConstraintViolation(context, "Password must contain at least one uppercase letter");
            return false;
        }
        
        // Check for at least one digit
        if (!password.matches(".*\\d.*")) {
            addConstraintViolation(context, "Password must contain at least one digit");
            return false;
        }
        
        // Check for at least one special character
        if (!password.matches(".*[@$!%*?&].*")) {
            addConstraintViolation(context, "Password must contain at least one special character (@$!%*?&)");
            return false;
        }
        
        // Check for common weak passwords
        if (isCommonPassword(password)) {
            addConstraintViolation(context, "Password is too common. Please choose a stronger password");
            return false;
        }
        
        return true;
    }
    
    private void addConstraintViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
    
    private boolean isCommonPassword(String password) {
        String lowerPassword = password.toLowerCase();
        
        // List of common weak passwords
        String[] commonPasswords = {
            "password", "123456", "12345678", "qwerty", "abc123", 
            "password123", "admin", "letmein", "welcome", "monkey",
            "1234567890", "password1", "123456789", "welcome123",
            "admin123", "root", "toor", "pass", "test", "guest"
        };
        
        for (String common : commonPasswords) {
            if (lowerPassword.contains(common)) {
                return true;
            }
        }
        
        // Check for sequential characters
        if (hasSequentialChars(password)) {
            return true;
        }
        
        // Check for repeated characters
        if (hasRepeatedChars(password)) {
            return true;
        }
        
        return false;
    }
    
    private boolean hasSequentialChars(String password) {
        String lowerPassword = password.toLowerCase();
        
        // Check for sequential letters (abc, def, etc.)
        for (int i = 0; i < lowerPassword.length() - 2; i++) {
            char c1 = lowerPassword.charAt(i);
            char c2 = lowerPassword.charAt(i + 1);
            char c3 = lowerPassword.charAt(i + 2);
            
            if (c2 == c1 + 1 && c3 == c2 + 1) {
                return true;
            }
        }
        
        // Check for sequential numbers (123, 456, etc.)
        String[] sequences = {"123", "234", "345", "456", "567", "678", "789", "890"};
        for (String seq : sequences) {
            if (lowerPassword.contains(seq)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean hasRepeatedChars(String password) {
        // Check for more than 2 consecutive repeated characters
        for (int i = 0; i < password.length() - 2; i++) {
            char c = password.charAt(i);
            if (password.charAt(i + 1) == c && password.charAt(i + 2) == c) {
                return true;
            }
        }
        return false;
    }
}
