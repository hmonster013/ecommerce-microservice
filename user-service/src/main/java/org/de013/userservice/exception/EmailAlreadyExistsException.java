package org.de013.userservice.exception;

import org.de013.common.exception.BusinessException;

/**
 * Exception thrown when trying to register with an email that already exists
 */
public class EmailAlreadyExistsException extends BusinessException {
    
    public EmailAlreadyExistsException(String email) {
        super("Email already exists: " + email);
    }
    
    public EmailAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public static EmailAlreadyExistsException forEmail(String email) {
        return new EmailAlreadyExistsException(email);
    }
}
