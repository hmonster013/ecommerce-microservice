package org.de013.userservice.util;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utility class for validation operations
 * Provides common validation methods and helpers
 */
public final class ValidationUtils {
    
    // Common patterns
    private static final Pattern ALPHA_PATTERN = Pattern.compile("^[a-zA-Z]+$");
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^\\d+$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{3,20}$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-ZÀ-ÿ\\s'-]{2,50}$");
    
    // Security patterns
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "(?i)(union|select|insert|update|delete|drop|create|alter|exec|script)"
    );
    private static final Pattern XSS_PATTERN = Pattern.compile(
        "(?i)(<script|javascript:|on\\w+\\s*=|<iframe|<object|<embed)"
    );
    
    private ValidationUtils() {
        // Utility class
    }
    
    /**
     * Check if string is null or empty
     */
    public static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
    
    /**
     * Check if string is not null and not empty
     */
    public static boolean isNotEmpty(String value) {
        return !isEmpty(value);
    }
    
    /**
     * Check if string length is within range
     */
    public static boolean isLengthValid(String value, int min, int max) {
        if (value == null) {
            return false;
        }
        int length = value.length();
        return length >= min && length <= max;
    }
    
    /**
     * Validate alphabetic characters only
     */
    public static boolean isAlpha(String value) {
        return isNotEmpty(value) && ALPHA_PATTERN.matcher(value).matches();
    }
    
    /**
     * Validate alphanumeric characters only
     */
    public static boolean isAlphanumeric(String value) {
        return isNotEmpty(value) && ALPHANUMERIC_PATTERN.matcher(value).matches();
    }
    
    /**
     * Validate numeric characters only
     */
    public static boolean isNumeric(String value) {
        return isNotEmpty(value) && NUMERIC_PATTERN.matcher(value).matches();
    }
    
    /**
     * Validate username format
     */
    public static boolean isValidUsername(String username) {
        return isNotEmpty(username) && USERNAME_PATTERN.matcher(username).matches();
    }
    
    /**
     * Validate name format (allows international characters)
     */
    public static boolean isValidName(String name) {
        return isNotEmpty(name) && NAME_PATTERN.matcher(name).matches();
    }
    
    /**
     * Check for potential SQL injection
     */
    public static boolean containsSqlInjection(String value) {
        return isNotEmpty(value) && SQL_INJECTION_PATTERN.matcher(value).find();
    }
    
    /**
     * Check for potential XSS
     */
    public static boolean containsXss(String value) {
        return isNotEmpty(value) && XSS_PATTERN.matcher(value).find();
    }
    
    /**
     * Sanitize string for security
     */
    public static String sanitize(String value) {
        if (isEmpty(value)) {
            return value;
        }
        
        // Remove potential XSS and SQL injection patterns
        String sanitized = value.replaceAll("(?i)<script[^>]*>.*?</script>", "")
                               .replaceAll("(?i)javascript:", "")
                               .replaceAll("(?i)on\\w+\\s*=", "")
                               .replaceAll("(?i)(union|select|insert|update|delete|drop|create|alter|exec)\\s", "");
        
        return sanitized.trim();
    }
    
    /**
     * Validate age range
     */
    public static boolean isValidAge(Integer age) {
        return age != null && age >= 13 && age <= 120;
    }
    
    /**
     * Validate positive number
     */
    public static boolean isPositive(Number number) {
        return number != null && number.doubleValue() > 0;
    }
    
    /**
     * Validate non-negative number
     */
    public static boolean isNonNegative(Number number) {
        return number != null && number.doubleValue() >= 0;
    }
    
    /**
     * Validate number within range
     */
    public static boolean isInRange(Number number, double min, double max) {
        if (number == null) {
            return false;
        }
        double value = number.doubleValue();
        return value >= min && value <= max;
    }
    
    /**
     * Convert validation errors to map
     */
    public static <T> Map<String, String> getValidationErrors(Set<ConstraintViolation<T>> violations) {
        Map<String, String> errors = new HashMap<>();
        
        for (ConstraintViolation<T> violation : violations) {
            String fieldName = violation.getPropertyPath().toString();
            String errorMessage = violation.getMessage();
            errors.put(fieldName, errorMessage);
        }
        
        return errors;
    }
    
    /**
     * Validate object and return errors
     */
    public static <T> Map<String, String> validate(T object, Validator validator) {
        Set<ConstraintViolation<T>> violations = validator.validate(object);
        return getValidationErrors(violations);
    }
    
    /**
     * Check if validation passed (no errors)
     */
    public static <T> boolean isValid(T object, Validator validator) {
        return validator.validate(object).isEmpty();
    }
    
    /**
     * Validate URL format
     */
    public static boolean isValidUrl(String url) {
        if (isEmpty(url)) {
            return false;
        }
        
        try {
            new java.net.URL(url);
            return true;
        } catch (java.net.MalformedURLException e) {
            return false;
        }
    }
    
    /**
     * Validate date format (ISO 8601)
     */
    public static boolean isValidIsoDate(String date) {
        if (isEmpty(date)) {
            return false;
        }
        
        try {
            java.time.LocalDate.parse(date);
            return true;
        } catch (java.time.format.DateTimeParseException e) {
            return false;
        }
    }
    
    /**
     * Validate datetime format (ISO 8601)
     */
    public static boolean isValidIsoDateTime(String dateTime) {
        if (isEmpty(dateTime)) {
            return false;
        }
        
        try {
            java.time.LocalDateTime.parse(dateTime);
            return true;
        } catch (java.time.format.DateTimeParseException e) {
            return false;
        }
    }
    
    /**
     * Validate UUID format
     */
    public static boolean isValidUuid(String uuid) {
        if (isEmpty(uuid)) {
            return false;
        }
        
        try {
            java.util.UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * Validate JSON format
     */
    public static boolean isValidJson(String json) {
        if (isEmpty(json)) {
            return false;
        }
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readTree(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Normalize whitespace in string
     */
    public static String normalizeWhitespace(String value) {
        if (isEmpty(value)) {
            return value;
        }
        
        return value.replaceAll("\\s+", " ").trim();
    }
    
    /**
     * Capitalize first letter of each word
     */
    public static String capitalizeWords(String value) {
        if (isEmpty(value)) {
            return value;
        }
        
        String[] words = value.toLowerCase().split("\\s+");
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                result.append(" ");
            }
            
            String word = words[i];
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1));
                }
            }
        }
        
        return result.toString();
    }
    
    /**
     * Check if string contains only printable ASCII characters
     */
    public static boolean isPrintableAscii(String value) {
        if (isEmpty(value)) {
            return true;
        }
        
        for (char c : value.toCharArray()) {
            if (c < 32 || c > 126) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Validate that string doesn't contain profanity
     */
    public static boolean containsProfanity(String value) {
        if (isEmpty(value)) {
            return false;
        }
        
        // Basic profanity check - in real application, use a comprehensive library
        String[] profanityWords = {"spam", "fake", "scam", "fraud"};
        String lowerValue = value.toLowerCase();
        
        for (String word : profanityWords) {
            if (lowerValue.contains(word)) {
                return true;
            }
        }
        
        return false;
    }
}
