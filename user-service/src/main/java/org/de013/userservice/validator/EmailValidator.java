package org.de013.userservice.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Email validator implementation
 * Validates email format, length, and domain restrictions
 */
public class EmailValidator implements ConstraintValidator<ValidEmail, String> {
    
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
        "guerrillamailblock.com", "pokemail.net", "spam4.me"
    );
    
    // Blocked domains for security reasons
    private static final Set<String> BLOCKED_DOMAINS = Set.of(
        "example.com", "test.com", "localhost", "invalid.com"
    );
    
    private boolean allowDisposable;
    private boolean checkDomain;

    @Override
    public void initialize(ValidEmail constraintAnnotation) {
        this.allowDisposable = constraintAnnotation.allowDisposable();
        this.checkDomain = constraintAnnotation.checkDomain();
    }
    
    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        
        email = email.trim().toLowerCase();
        
        // Check length limits
        if (email.length() > MAX_EMAIL_LENGTH) {
            addConstraintViolation(context, "email.tooLong");
            return false;
        }

        // Check basic format
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            addConstraintViolation(context, "email.format");
            return false;
        }

        // Split email into local and domain parts
        String[] parts = email.split("@");
        if (parts.length != 2) {
            addConstraintViolation(context, "email.format");
            return false;
        }
        
        String localPart = parts[0];
        String domain = parts[1];
        
        // Validate local part
        if (!isValidLocalPart(localPart, context)) {
            return false;
        }
        
        // Validate domain
        if (!isValidDomain(domain, context)) {
            return false;
        }
        
        // Check for blocked domains
        if (BLOCKED_DOMAINS.contains(domain)) {
            addConstraintViolation(context, "email.blocked");
            return false;
        }

        // Check for disposable domains if not allowed
        if (!allowDisposable && DISPOSABLE_DOMAINS.contains(domain)) {
            addConstraintViolation(context, "email.disposable");
            return false;
        }
        
        return true;
    }
    
    private boolean isValidLocalPart(String localPart, ConstraintValidatorContext context) {
        // Check length
        if (localPart.length() > MAX_LOCAL_PART_LENGTH) {
            addConstraintViolation(context, "email.localPartTooLong");
            return false;
        }

        // Check for empty local part
        if (localPart.isEmpty()) {
            addConstraintViolation(context, "email.domainRequired");
            return false;
        }

        // Check for consecutive dots
        if (localPart.contains("..")) {
            addConstraintViolation(context, "email.consecutiveDots");
            return false;
        }

        // Check for dots at start or end
        if (localPart.startsWith(".") || localPart.endsWith(".")) {
            if (localPart.startsWith(".")) {
                addConstraintViolation(context, "email.invalidStart");
            } else {
                addConstraintViolation(context, "email.invalidEnd");
            }
            return false;
        }

        // Check for invalid characters (basic check)
        if (!localPart.matches("[a-zA-Z0-9._%+-]+")) {
            addConstraintViolation(context, "email.invalidCharacters");
            return false;
        }

        return true;
    }
    
    private boolean isValidDomain(String domain, ConstraintValidatorContext context) {
        // Check for empty domain
        if (domain.isEmpty()) {
            addConstraintViolation(context, "email.domainRequired");
            return false;
        }

        // Check domain length
        if (domain.length() > 253) {
            addConstraintViolation(context, "email.domainTooLong");
            return false;
        }

        // Check for consecutive dots
        if (domain.contains("..")) {
            addConstraintViolation(context, "email.consecutiveDots");
            return false;
        }

        // Check for dots at start or end
        if (domain.startsWith(".") || domain.endsWith(".")) {
            if (domain.startsWith(".")) {
                addConstraintViolation(context, "email.invalidStart");
            } else {
                addConstraintViolation(context, "email.invalidEnd");
            }
            return false;
        }

        // Check for valid domain format
        if (!domain.matches("[a-zA-Z0-9.-]+")) {
            addConstraintViolation(context, "email.invalidCharacters");
            return false;
        }

        // Check for at least one dot (TLD requirement)
        if (!domain.contains(".")) {
            addConstraintViolation(context, "email.tldRequired");
            return false;
        }

        // Check TLD length (at least 2 characters)
        String[] domainParts = domain.split("\\.");
        String tld = domainParts[domainParts.length - 1];
        if (tld.length() < 2) {
            addConstraintViolation(context, "email.tldTooShort");
            return false;
        }

        return true;
    }
    
    private void addConstraintViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}
