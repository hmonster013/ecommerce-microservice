package org.de013.userservice.exception;

import org.de013.common.exception.ResourceNotFoundException;

/**
 * Exception thrown when a user is not found
 */
public class UserNotFoundException extends ResourceNotFoundException {
    
    public UserNotFoundException(String message) {
        super(message);
    }
    
    public UserNotFoundException(Long userId) {
        super("User not found with id: " + userId);
    }
    
    public UserNotFoundException(String field, String value) {
        super("User not found with " + field + ": " + value);
    }
    
    public static UserNotFoundException byId(Long id) {
        return new UserNotFoundException(id);
    }
    
    public static UserNotFoundException byEmail(String email) {
        return new UserNotFoundException("email", email);
    }
    
    public static UserNotFoundException byUsername(String username) {
        return new UserNotFoundException("username", username);
    }
}
