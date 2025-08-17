package org.de013.userservice.util;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utility class for email operations
 * Provides methods for email validation, normalization, and domain checks
 */
public final class EmailUtils {
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    
    private static final int MAX_EMAIL_LENGTH = 254; // RFC 5321 limit
    private static final int MAX_LOCAL_PART_LENGTH = 64; // RFC 5321 limit
    
    // Common disposable email domains
    private static final Set<String> DISPOSABLE_DOMAINS = Set.of(
        "10minutemail.com", "guerrillamail.com", "mailinator.com",
        "tempmail.org", "throwaway.email", "temp-mail.org",
        "yopmail.com", "maildrop.cc", "sharklasers.com",
        "guerrillamailblock.com", "pokemail.net", "spam4.me",
        "dispostable.com", "fakeinbox.com", "getnada.com",
        "harakirimail.com", "incognitomail.org", "jetable.org",
        "mytrashmail.com", "no-spam.ws", "noclickemail.com",
        "trashmail.ws", "wegwerfmail.de", "zehnminutenmail.de"
    );
    
    // Blocked domains for security reasons
    private static final Set<String> BLOCKED_DOMAINS = Set.of(
        "example.com", "test.com", "localhost", "invalid.com",
        "example.org", "example.net", "test.org", "test.net"
    );
    
    // Common email providers
    private static final Set<String> COMMON_PROVIDERS = Set.of(
        "gmail.com", "yahoo.com", "hotmail.com", "outlook.com",
        "aol.com", "icloud.com", "protonmail.com", "mail.com",
        "zoho.com", "yandex.com", "gmx.com", "fastmail.com"
    );
    
    private EmailUtils() {
        // Utility class
    }
    
    /**
     * Normalize email address (lowercase, trim)
     */
    public static String normalize(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase();
    }
    
    /**
     * Validate email format
     */
    public static boolean isValidFormat(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        
        String normalizedEmail = normalize(email);
        return EMAIL_PATTERN.matcher(normalizedEmail).matches();
    }
    
    /**
     * Check if email length is within limits
     */
    public static boolean isValidLength(String email) {
        if (email == null) {
            return false;
        }
        
        if (email.length() > MAX_EMAIL_LENGTH) {
            return false;
        }
        
        String[] parts = email.split("@");
        if (parts.length != 2) {
            return false;
        }
        
        return parts[0].length() <= MAX_LOCAL_PART_LENGTH;
    }
    
    /**
     * Extract domain from email
     */
    public static String extractDomain(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        
        String[] parts = email.split("@");
        if (parts.length != 2) {
            return null;
        }
        
        return parts[1].toLowerCase();
    }
    
    /**
     * Extract local part from email
     */
    public static String extractLocalPart(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        
        String[] parts = email.split("@");
        if (parts.length != 2) {
            return null;
        }
        
        return parts[0];
    }
    
    /**
     * Check if email domain is disposable
     */
    public static boolean isDisposableDomain(String email) {
        String domain = extractDomain(email);
        return domain != null && DISPOSABLE_DOMAINS.contains(domain);
    }
    
    /**
     * Check if email domain is blocked
     */
    public static boolean isBlockedDomain(String email) {
        String domain = extractDomain(email);
        return domain != null && BLOCKED_DOMAINS.contains(domain);
    }
    
    /**
     * Check if email is from a common provider
     */
    public static boolean isCommonProvider(String email) {
        String domain = extractDomain(email);
        return domain != null && COMMON_PROVIDERS.contains(domain);
    }
    
    /**
     * Check if email is corporate (not from common providers)
     */
    public static boolean isCorporateEmail(String email) {
        return isValidFormat(email) && !isCommonProvider(email) && !isDisposableDomain(email);
    }
    
    /**
     * Generate email suggestions for typos
     */
    public static String suggestCorrection(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        
        String domain = extractDomain(email);
        if (domain == null) {
            return null;
        }
        
        // Common typo corrections
        String correctedDomain = correctCommonTypos(domain);
        if (!correctedDomain.equals(domain)) {
            return extractLocalPart(email) + "@" + correctedDomain;
        }
        
        return null;
    }
    
    /**
     * Mask email for privacy (e.g., j***@example.com)
     */
    public static String maskEmail(String email) {
        if (!isValidFormat(email)) {
            return email;
        }
        
        String localPart = extractLocalPart(email);
        String domain = extractDomain(email);
        
        if (localPart == null || domain == null) {
            return email;
        }
        
        String maskedLocal;
        if (localPart.length() <= 2) {
            maskedLocal = "*".repeat(localPart.length());
        } else {
            maskedLocal = localPart.charAt(0) + "*".repeat(localPart.length() - 2) + localPart.charAt(localPart.length() - 1);
        }
        
        return maskedLocal + "@" + domain;
    }
    
    /**
     * Check if two emails are similar (same after normalization)
     */
    public static boolean areSimilar(String email1, String email2) {
        if (email1 == null || email2 == null) {
            return false;
        }
        
        return normalize(email1).equals(normalize(email2));
    }
    
    /**
     * Validate email for business use (not disposable, not blocked)
     */
    public static boolean isBusinessEmail(String email) {
        return isValidFormat(email) && 
               isValidLength(email) && 
               !isDisposableDomain(email) && 
               !isBlockedDomain(email);
    }
    
    /**
     * Get email validation score (0-100)
     */
    public static int getEmailScore(String email) {
        if (!isValidFormat(email)) {
            return 0;
        }
        
        int score = 50; // Base score for valid format
        
        // Length check
        if (isValidLength(email)) score += 10;
        
        // Domain checks
        if (!isDisposableDomain(email)) score += 15;
        if (!isBlockedDomain(email)) score += 10;
        
        // Provider checks
        if (isCommonProvider(email)) score += 10;
        else if (isCorporateEmail(email)) score += 15;
        
        return Math.min(100, score);
    }
    
    /**
     * Get email quality description
     */
    public static String getEmailQualityDescription(int score) {
        if (score < 30) return "Invalid";
        if (score < 50) return "Poor";
        if (score < 70) return "Fair";
        if (score < 90) return "Good";
        return "Excellent";
    }
    
    private static String correctCommonTypos(String domain) {
        // Gmail typos
        if (domain.matches("g?mail\\.(com?|co)")) return "gmail.com";
        if (domain.equals("gmai.com") || domain.equals("gmial.com")) return "gmail.com";
        
        // Yahoo typos
        if (domain.matches("yaho+\\.com?") || domain.equals("yahooo.com")) return "yahoo.com";
        
        // Hotmail/Outlook typos
        if (domain.matches("hotmai?l\\.(com?|co)")) return "hotmail.com";
        if (domain.matches("outloo?k\\.(com?|co)")) return "outlook.com";
        
        // Common TLD typos
        if (domain.endsWith(".co")) return domain + "m";
        if (domain.endsWith(".om")) return domain.substring(0, domain.length() - 2) + "com";
        
        return domain;
    }
}
