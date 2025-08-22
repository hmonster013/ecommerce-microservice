package org.de013.userservice.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.de013.userservice.util.ValidationUtils;

import java.util.regex.Pattern;

/**
 * Name validator implementation
 * Validates name format, length, and character restrictions
 */
public class NameValidator implements ConstraintValidator<ValidName, String> {
    
    // Pattern for names with special characters (apostrophes, hyphens, spaces)
    private static final Pattern NAME_WITH_SPECIAL_PATTERN = Pattern.compile(
        "^[a-zA-ZÀ-ÿ\\s'-]{2,50}$"
    );
    
    // Pattern for names without special characters
    private static final Pattern NAME_SIMPLE_PATTERN = Pattern.compile(
        "^[a-zA-ZÀ-ÿ\\s]{2,50}$"
    );
    
    private int min;
    private int max;
    private boolean allowSpecialChars;
    
    @Override
    public void initialize(ValidName constraintAnnotation) {
        this.min = constraintAnnotation.min();
        this.max = constraintAnnotation.max();
        this.allowSpecialChars = constraintAnnotation.allowSpecialChars();
    }
    
    @Override
    public boolean isValid(String name, ConstraintValidatorContext context) {
        if (name == null) {
            return false;
        }
        
        // Trim and normalize whitespace
        name = ValidationUtils.normalizeWhitespace(name);
        
        // Check length
        if (name.length() < min) {
            addConstraintViolation(context, "validation.tooShort");
            return false;
        }
        if (name.length() > max) {
            addConstraintViolation(context, "validation.tooLong");
            return false;
        }

        // Check for empty after trimming
        if (name.trim().isEmpty()) {
            addConstraintViolation(context, "fullName.required");
            return false;
        }
        
        // Check pattern based on special character allowance
        Pattern pattern = allowSpecialChars ? NAME_WITH_SPECIAL_PATTERN : NAME_SIMPLE_PATTERN;
        if (!pattern.matcher(name).matches()) {
            addConstraintViolation(context, "fullName.invalid");
            return false;
        }
        
        // Check for consecutive spaces
        if (name.contains("  ")) {
            addConstraintViolation(context, "fullName.invalid");
            return false;
        }

        // Check for leading/trailing spaces
        if (!name.equals(name.trim())) {
            addConstraintViolation(context, "fullName.invalid");
            return false;
        }

        // Check for only spaces
        if (name.trim().isEmpty()) {
            addConstraintViolation(context, "fullName.required");
            return false;
        }

        // Check for security issues
        if (ValidationUtils.containsSqlInjection(name) || ValidationUtils.containsXss(name)) {
            addConstraintViolation(context, "security.suspicious");
            return false;
        }

        // Check for profanity
        if (ValidationUtils.containsProfanity(name)) {
            addConstraintViolation(context, "security.profanity");
            return false;
        }

        // Check for numeric-only names
        if (ValidationUtils.isNumeric(name.replaceAll("\\s", ""))) {
            addConstraintViolation(context, "fullName.invalid");
            return false;
        }
        
        return true;
    }
    
    private void addConstraintViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}
