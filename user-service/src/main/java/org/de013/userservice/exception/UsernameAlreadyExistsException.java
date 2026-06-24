package org.de013.userservice.exception;

import org.de013.common.exception.BusinessException;

/**
 * Exception thrown when trying to register with a username that already exists
 */
public class UsernameAlreadyExistsException extends BusinessException {

    public UsernameAlreadyExistsException(String username) {
        super("Username already exists: " + username);
    }

    public UsernameAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }

    public static UsernameAlreadyExistsException forUsername(String username) {
        return new UsernameAlreadyExistsException(username);
    }
}
