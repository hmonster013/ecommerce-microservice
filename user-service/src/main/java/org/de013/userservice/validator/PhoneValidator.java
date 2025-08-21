package org.de013.userservice.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Phone number validator implementation
 * Validates phone number format for various countries
 */
public class PhoneValidator implements ConstraintValidator<ValidPhone, String> {
    
    // International phone number pattern (E.164 format)
    private static final Pattern INTERNATIONAL_PATTERN = Pattern.compile(
        "^\\+[1-9]\\d{1,14}$"
    );
    
    // Vietnam phone number patterns
    private static final Pattern VN_MOBILE_PATTERN = Pattern.compile(
        "^(\\+84|84|0)(3[2-9]|5[689]|7[06-9]|8[1-689]|9[0-46-9])\\d{7}$"
    );
    
    // US phone number pattern
    private static final Pattern US_PATTERN = Pattern.compile(
        "^(\\+1|1)?[2-9]\\d{2}[2-9]\\d{2}\\d{4}$"
    );
    
    // General patterns for common formats
    private static final Pattern GENERAL_PATTERN = Pattern.compile(
        "^[\\+]?[1-9]\\d{1,14}$"
    );
    
    private boolean international;
    private String countryCode;

    @Override
    public void initialize(ValidPhone constraintAnnotation) {
        this.international = constraintAnnotation.international();
        this.countryCode = constraintAnnotation.countryCode().toUpperCase();
    }
    
    @Override
    public boolean isValid(String phone, ConstraintValidatorContext context) {
        if (phone == null || phone.trim().isEmpty()) {
            return true; // Let @NotNull handle null validation
        }
        
        // Clean phone number (remove spaces, dashes, parentheses)
        String cleanPhone = phone.replaceAll("[\\s\\-\\(\\)]", "");
        
        // Check length limits
        if (cleanPhone.length() < 7) {
            addConstraintViolation(context, "phone.tooShort");
            return false;
        }
        if (cleanPhone.length() > 17) {
            addConstraintViolation(context, "phone.tooLong");
            return false;
        }
        
        // Validate based on country code
        if (!countryCode.isEmpty()) {
            return validateByCountry(cleanPhone, context);
        }
        
        // General validation
        if (international) {
            // Check international format first
            if (cleanPhone.startsWith("+")) {
                if (!INTERNATIONAL_PATTERN.matcher(cleanPhone).matches()) {
                    addConstraintViolation(context, "phone.invalidFormat");
                    return false;
                }
            } else {
                // Check general format
                if (!GENERAL_PATTERN.matcher(cleanPhone).matches()) {
                    addConstraintViolation(context, "phone.format");
                    return false;
                }
            }
        } else {
            // Only allow local format
            if (cleanPhone.startsWith("+")) {
                addConstraintViolation(context, "phone.internationalNotAllowed");
                return false;
            }

            if (!GENERAL_PATTERN.matcher(cleanPhone).matches()) {
                addConstraintViolation(context, "phone.format");
                return false;
            }
        }
        
        return true;
    }
    
    private boolean validateByCountry(String phone, ConstraintValidatorContext context) {
        switch (countryCode) {
            case "VN":
                return validateVietnamesePhone(phone, context);
            case "US":
                return validateUSPhone(phone, context);
            default:
                // Fallback to general validation
                if (!GENERAL_PATTERN.matcher(phone).matches()) {
                    addConstraintViolation(context, "phone.invalidCountry");
                    return false;
                }
                return true;
        }
    }
    
    private boolean validateVietnamesePhone(String phone, ConstraintValidatorContext context) {
        if (!VN_MOBILE_PATTERN.matcher(phone).matches()) {
            addConstraintViolation(context, "phone.invalidVietnamese");
            return false;
        }
        return true;
    }
    
    private boolean validateUSPhone(String phone, ConstraintValidatorContext context) {
        if (!US_PATTERN.matcher(phone).matches()) {
            addConstraintViolation(context, "phone.invalidUS");
            return false;
        }
        return true;
    }
    
    private void addConstraintViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}
