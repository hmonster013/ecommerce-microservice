package org.de013.productcatalog.exception;

import org.springframework.http.HttpStatus;

public class ReservationException extends BaseBusinessException {
    public ReservationException(String code, HttpStatus status) {
        super(code, code, status);
    }
}
