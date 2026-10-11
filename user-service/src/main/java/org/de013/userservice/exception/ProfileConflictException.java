package org.de013.userservice.exception;

import org.de013.common.exception.BusinessException;

public class ProfileConflictException extends BusinessException {

    public ProfileConflictException(String message) {
        super(message);
    }

    public ProfileConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
