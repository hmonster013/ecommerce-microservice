package org.de013.userservice.exception;

import org.de013.common.exception.BusinessException;

/**
 * Exception thrown when user account has status issues
 */
public class AccountStatusException extends BusinessException {
    
    public AccountStatusException(String message) {
        super(message);
    }
    
    public AccountStatusException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public static AccountStatusException inactive() {
        return new AccountStatusException("User account is inactive");
    }
    
    public static AccountStatusException suspended() {
        return new AccountStatusException("User account is suspended");
    }
    
    public static AccountStatusException deleted() {
        return new AccountStatusException("User account has been deleted");
    }
    
    public static AccountStatusException pendingVerification() {
        return new AccountStatusException("User account is pending email verification");
    }
}
