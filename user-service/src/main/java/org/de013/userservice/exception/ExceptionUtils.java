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
}
