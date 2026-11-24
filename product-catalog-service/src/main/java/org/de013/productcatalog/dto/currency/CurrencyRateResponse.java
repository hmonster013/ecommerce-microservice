package org.de013.productcatalog.dto.currency;

public record CurrencyRateResponse(String currency, String rate, boolean enabled, int minorUnits) {
}
