package org.de013.productcatalog.exception;

import org.springframework.http.HttpStatus;

public class CurrencySettingsException extends BaseBusinessException {
    private String currency;

    public String getCurrency() {
        return currency;
    }

    public static CurrencySettingsException invalidRate(String message, String currency) {
        CurrencySettingsException exception = invalid(message);
        exception.currency = currency;
        return exception;
    }
    private CurrencySettingsException(String message, String code, HttpStatus status) {
        super(message, code, status);
    }

    public static CurrencySettingsException invalid(String message) {
        return new CurrencySettingsException(message, "INVALID_CURRENCY_SETTINGS", HttpStatus.BAD_REQUEST);
    }

    public static CurrencySettingsException conflict() {
        return new CurrencySettingsException("Currency settings were changed by another administrator", "CURRENCY_SETTINGS_CONFLICT", HttpStatus.CONFLICT);
    }
}
