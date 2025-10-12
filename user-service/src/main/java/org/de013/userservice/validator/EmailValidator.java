package org.de013.userservice.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Email validator implementation for Jakarta Bean Validation
 *
 * <p>Validates email addresses according to RFC 5321 and RFC 1035 standards:</p>
 * <ul>
 *   <li>Email format validation using regex pattern</li>
 *   <li>Length restrictions (max 254 chars for email, 64 for local part, 253 for domain)</li>
 *   <li>Local part validation (no consecutive dots, valid characters)</li>
 *   <li>Domain validation (proper labels, TLD requirements)</li>
 *   <li>Disposable email domain detection</li>
 *   <li>Blocked domain filtering</li>
 * </ul>
 *
 * @see ValidEmail
 * @see ConstraintValidator
 */
public class EmailValidator implements ConstraintValidator<ValidEmail, String> {
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    
    private static final int MAX_EMAIL_LENGTH = 254; // RFC 5321 limit
    private static final int MAX_LOCAL_PART_LENGTH = 64; // RFC 5321 limit
    private static final int MAX_DOMAIN_LENGTH = 253; // RFC 1035 limit
    private static final int MIN_TLD_LENGTH = 2; // Minimum TLD length

    // Common disposable email domains - expanded list for better security
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

    // Blocked domains for security and testing reasons
    private static final Set<String> BLOCKED_DOMAINS = Set.of(
        "example.com", "test.com", "localhost", "invalid.com",
        "example.org", "example.net", "test.org", "test.net"
    );
    
    private boolean allowDisposable;

    @Override
    public void initialize(ValidEmail constraintAnnotation) {
        this.allowDisposable = constraintAnnotation.allowDisposable();
    }
    
    /**
     * Validates the email address according to RFC standards and business rules
     *
     * @param email the email address to validate
     * @param context the constraint validator context for custom error messages
     * @return true if the email is valid, false otherwise
     */
    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        // Null or empty check
        if (email == null || email.trim().isEmpty()) {
            return false;
        }

        // Normalize email (trim and lowercase)
        email = email.trim().toLowerCase();

        // Check overall length limit (RFC 5321)
        if (email.length() > MAX_EMAIL_LENGTH) {
            addConstraintViolation(context, "email.tooLong");
            return false;
        }

        // Check basic format using regex
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

        // Validate local part (before @)
        if (!isValidLocalPart(localPart, context)) {
            return false;
        }

        // Validate domain (after @)
        if (!isValidDomain(domain, context)) {
            return false;
        }

        // Check for blocked domains (security)
        if (BLOCKED_DOMAINS.contains(domain)) {
            addConstraintViolation(context, "email.blocked");
            return false;
        }

        // Check for disposable domains if not allowed (business rule)
        if (!allowDisposable && DISPOSABLE_DOMAINS.contains(domain)) {
            addConstraintViolation(context, "email.disposable");
            return false;
        }

        return true;
    }
    
    /**
     * Validates the local part of email (before @) according to RFC 5321
     *
     * @param localPart the local part to validate
     * @param context the constraint validator context
     * @return true if valid, false otherwise
     */
    private boolean isValidLocalPart(String localPart, ConstraintValidatorContext context) {
        // Check for empty local part
        if (localPart.isEmpty()) {
            addConstraintViolation(context, "email.localPartRequired");
            return false;
        }

        // Check length (RFC 5321: max 64 characters)
        if (localPart.length() > MAX_LOCAL_PART_LENGTH) {
            addConstraintViolation(context, "email.localPartTooLong");
            return false;
        }

        // Check for consecutive dots (not allowed)
        if (localPart.contains("..")) {
            addConstraintViolation(context, "email.consecutiveDots");
            return false;
        }

        // Check for dots at start or end (not allowed)
        if (localPart.startsWith(".") || localPart.endsWith(".")) {
            if (localPart.startsWith(".")) {
                addConstraintViolation(context, "email.invalidStart");
            } else {
                addConstraintViolation(context, "email.invalidEnd");
            }
            return false;
        }

        // Check for valid characters (alphanumeric and ._%+-)
        if (!localPart.matches("[a-zA-Z0-9._%+-]+")) {
            addConstraintViolation(context, "email.invalidCharacters");
            return false;
        }

        return true;
    }
    
    /**
     * Validates the domain part of email (after @) according to RFC 1035
     *
     * @param domain the domain to validate
     * @param context the constraint validator context
     * @return true if valid, false otherwise
     */
    private boolean isValidDomain(String domain, ConstraintValidatorContext context) {
        // Check for empty domain
        if (domain.isEmpty()) {
            addConstraintViolation(context, "email.domainRequired");
            return false;
        }

        // Check domain length (RFC 1035: max 253 characters)
        if (domain.length() > MAX_DOMAIN_LENGTH) {
            addConstraintViolation(context, "email.domainTooLong");
            return false;
        }

        // Check for consecutive dots (not allowed)
        if (domain.contains("..")) {
            addConstraintViolation(context, "email.consecutiveDots");
            return false;
        }

        // Check for dots at start or end (not allowed)
        if (domain.startsWith(".") || domain.endsWith(".")) {
            if (domain.startsWith(".")) {
                addConstraintViolation(context, "email.invalidStart");
            } else {
                addConstraintViolation(context, "email.invalidEnd");
            }
            return false;
        }

        // Check for valid domain format (alphanumeric, dots, hyphens)
        if (!domain.matches("[a-zA-Z0-9.-]+")) {
            addConstraintViolation(context, "email.invalidCharacters");
            return false;
        }

        // Check for at least one dot (TLD requirement)
        if (!domain.contains(".")) {
            addConstraintViolation(context, "email.tldRequired");
            return false;
        }

        // Split domain into labels and validate
        String[] domainParts = domain.split("\\.");

        // Check TLD length (minimum 2 characters)
        String tld = domainParts[domainParts.length - 1];
        if (tld.length() < MIN_TLD_LENGTH) {
            addConstraintViolation(context, "email.tldTooShort");
            return false;
        }

        // Validate each domain label according to RFC 1035
        for (String label : domainParts) {
            if (!isValidDomainLabel(label)) {
                addConstraintViolation(context, "email.invalidDomainLabel");
                return false;
            }
        }

        return true;
    }

    /**
     * Validates individual domain label according to RFC 1035
     *
     * <p>Rules:</p>
     * <ul>
     *   <li>Must be 1-63 characters long</li>
     *   <li>Must not start or end with hyphen</li>
     *   <li>Must contain only alphanumeric characters and hyphens</li>
     * </ul>
     *
     * @param label the domain label to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidDomainLabel(String label) {
        // Check length (RFC 1035: 1-63 characters per label)
        if (label.isEmpty() || label.length() > 63) {
            return false;
        }

        // Label cannot start or end with hyphen (RFC 1035)
        if (label.startsWith("-") || label.endsWith("-")) {
            return false;
        }

        // Label must contain only alphanumeric and hyphens
        return label.matches("[a-zA-Z0-9-]+");
    }

    /**
     * Adds a custom constraint violation message to the context
     *
     * @param context the constraint validator context
     * @param message the message template key
     */
    private void addConstraintViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}
