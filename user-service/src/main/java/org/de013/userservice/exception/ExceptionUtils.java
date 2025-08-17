package org.de013.userservice.exception;

/**
 * Utility class for creating and throwing exceptions
 */
public final class ExceptionUtils {
    
    private ExceptionUtils() {
        // Utility class
    }
    
    /**
     * Throw UserNotFoundException if user is null
     */
    public static void throwIfUserNotFound(Object user, Long userId) {
        if (user == null) {
            throw UserNotFoundException.byId(userId);
        }
    }
    
    /**
     * Throw UserNotFoundException if user is null
     */
    public static void throwIfUserNotFound(Object user, String email) {
        if (user == null) {
            throw UserNotFoundException.byEmail(email);
        }
    }
    
    /**
     * Throw EmailAlreadyExistsException if email exists
     */
    public static void throwIfEmailExists(boolean emailExists, String email) {
        if (emailExists) {
            throw EmailAlreadyExistsException.forEmail(email);
        }
    }
    
    /**
     * Throw AccountStatusException if account is not active
     */
    public static void throwIfAccountInactive(boolean isActive) {
        if (!isActive) {
            throw AccountStatusException.inactive();
        }
    }
    
    /**
     * Throw InvalidTokenException if token is null or empty
     */
    public static void throwIfTokenEmpty(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw InvalidTokenException.empty();
        }
    }
    
    /**
     * Throw InvalidCredentialsException for authentication failures
     */
    public static void throwInvalidCredentials() {
        throw InvalidCredentialsException.invalidEmailOrPassword();
    }
    
    /**
     * Throw InvalidCredentialsException for locked account
     */
    public static void throwAccountLocked() {
        throw InvalidCredentialsException.accountLocked();
    }
    
    /**
     * Throw InvalidCredentialsException for disabled account
     */
    public static void throwAccountDisabled() {
        throw InvalidCredentialsException.accountDisabled();
    }
}
