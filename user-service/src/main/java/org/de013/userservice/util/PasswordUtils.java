package org.de013.userservice.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Utility class for password operations
 * Provides methods for password validation, generation, and security checks
 */
public final class PasswordUtils {
    
    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    // Password strength patterns
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile(".*[a-z].*");
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*\\d.*");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile(".*[@$!%*?&].*");
    
    // Common weak passwords
    private static final String[] COMMON_PASSWORDS = {
        "password", "123456", "12345678", "qwerty", "abc123", 
        "password123", "admin", "letmein", "welcome", "monkey",
        "1234567890", "password1", "123456789", "welcome123",
        "admin123", "root", "toor", "pass", "test", "guest"
    };
    
    private PasswordUtils() {
        // Utility class
    }
    
    /**
     * Encode password using BCrypt
     */
    public static String encode(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        return PASSWORD_ENCODER.encode(rawPassword);
    }
    
    /**
     * Verify password against encoded hash
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return PASSWORD_ENCODER.matches(rawPassword, encodedPassword);
    }
    
    /**
     * Check if password needs rehashing (for security upgrades)
     */
    public static boolean needsRehashing(String encodedPassword) {
        // BCrypt with strength 12 should start with $2a$12$ or $2b$12$
        return encodedPassword == null || 
               (!encodedPassword.startsWith("$2a$12$") && !encodedPassword.startsWith("$2b$12$"));
    }
    
    /**
     * Generate a secure random password
     */
    public static String generateSecurePassword(int length) {
        if (length < 8) {
            throw new IllegalArgumentException("Password length must be at least 8 characters");
        }
        
        String lowercase = "abcdefghijklmnopqrstuvwxyz";
        String uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String digits = "0123456789";
        String specialChars = "@$!%*?&";
        String allChars = lowercase + uppercase + digits + specialChars;
        
        StringBuilder password = new StringBuilder();
        
        // Ensure at least one character from each category
        password.append(lowercase.charAt(SECURE_RANDOM.nextInt(lowercase.length())));
        password.append(uppercase.charAt(SECURE_RANDOM.nextInt(uppercase.length())));
        password.append(digits.charAt(SECURE_RANDOM.nextInt(digits.length())));
        password.append(specialChars.charAt(SECURE_RANDOM.nextInt(specialChars.length())));
        
        // Fill the rest randomly
        for (int i = 4; i < length; i++) {
            password.append(allChars.charAt(SECURE_RANDOM.nextInt(allChars.length())));
        }
        
        // Shuffle the password
        return shuffleString(password.toString());
    }
    
    /**
     * Generate a temporary password for password reset
     */
    public static String generateTemporaryPassword() {
        return generateSecurePassword(12);
    }
    
    /**
     * Calculate password strength score (0-100)
     */
    public static int calculatePasswordStrength(String password) {
        if (password == null || password.isEmpty()) {
            return 0;
        }
        
        int score = 0;
        
        // Length score (max 25 points)
        if (password.length() >= 8) score += 10;
        if (password.length() >= 12) score += 10;
        if (password.length() >= 16) score += 5;
        
        // Character variety (max 40 points)
        if (LOWERCASE_PATTERN.matcher(password).matches()) score += 10;
        if (UPPERCASE_PATTERN.matcher(password).matches()) score += 10;
        if (DIGIT_PATTERN.matcher(password).matches()) score += 10;
        if (SPECIAL_CHAR_PATTERN.matcher(password).matches()) score += 10;
        
        // Complexity bonus (max 20 points)
        if (password.length() > 12 && hasVariedCharacters(password)) score += 10;
        if (!isCommonPassword(password)) score += 10;
        
        // Penalty for common patterns (max -15 points)
        if (hasSequentialChars(password)) score -= 5;
        if (hasRepeatedChars(password)) score -= 5;
        if (isKeyboardPattern(password)) score -= 5;
        
        return Math.max(0, Math.min(100, score));
    }
    
    /**
     * Get password strength description
     */
    public static String getPasswordStrengthDescription(int score) {
        if (score < 30) return "Very Weak";
        if (score < 50) return "Weak";
        if (score < 70) return "Fair";
        if (score < 90) return "Good";
        return "Excellent";
    }
    
    /**
     * Validate password strength
     */
    public static boolean isStrongPassword(String password) {
        return calculatePasswordStrength(password) >= 70;
    }
    
    /**
     * Check if password is commonly used
     */
    public static boolean isCommonPassword(String password) {
        if (password == null) return true;
        
        String lowerPassword = password.toLowerCase();
        for (String common : COMMON_PASSWORDS) {
            if (lowerPassword.contains(common)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if password contains user information
     */
    public static boolean containsUserInfo(String password, String email, String firstName, String lastName) {
        if (password == null) return false;
        
        String lowerPassword = password.toLowerCase();
        
        if (email != null && !email.isEmpty()) {
            String emailLocal = email.split("@")[0].toLowerCase();
            if (lowerPassword.contains(emailLocal)) return true;
        }
        
        if (firstName != null && firstName.length() > 2) {
            if (lowerPassword.contains(firstName.toLowerCase())) return true;
        }
        
        if (lastName != null && lastName.length() > 2) {
            if (lowerPassword.contains(lastName.toLowerCase())) return true;
        }
        
        return false;
    }
    
    private static String shuffleString(String string) {
        char[] chars = string.toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = SECURE_RANDOM.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }
        return new String(chars);
    }
    
    private static boolean hasVariedCharacters(String password) {
        int categories = 0;
        if (LOWERCASE_PATTERN.matcher(password).matches()) categories++;
        if (UPPERCASE_PATTERN.matcher(password).matches()) categories++;
        if (DIGIT_PATTERN.matcher(password).matches()) categories++;
        if (SPECIAL_CHAR_PATTERN.matcher(password).matches()) categories++;
        return categories >= 3;
    }
    
    private static boolean hasSequentialChars(String password) {
        String lowerPassword = password.toLowerCase();
        for (int i = 0; i < lowerPassword.length() - 2; i++) {
            char c1 = lowerPassword.charAt(i);
            char c2 = lowerPassword.charAt(i + 1);
            char c3 = lowerPassword.charAt(i + 2);
            if (c2 == c1 + 1 && c3 == c2 + 1) {
                return true;
            }
        }
        return false;
    }
    
    private static boolean hasRepeatedChars(String password) {
        for (int i = 0; i < password.length() - 2; i++) {
            char c = password.charAt(i);
            if (password.charAt(i + 1) == c && password.charAt(i + 2) == c) {
                return true;
            }
        }
        return false;
    }
    
    private static boolean isKeyboardPattern(String password) {
        String[] patterns = {"qwerty", "asdf", "zxcv", "123456", "098765"};
        String lowerPassword = password.toLowerCase();
        for (String pattern : patterns) {
            if (lowerPassword.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}
