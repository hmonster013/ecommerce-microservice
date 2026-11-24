package org.de013.productcatalog.config;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CurrencyRegistry {
    private static final Map<String, Integer> MINOR_UNITS = Map.of(
            "USD", 2, "VND", 0, "EUR", 2, "GBP", 2, "JPY", 0,
            "CNY", 2, "AUD", 2, "SGD", 2, "KRW", 0);

    public boolean isSupported(String currency) {
        return currency != null && MINOR_UNITS.containsKey(currency);
    }

    public int minorUnits(String currency) {
        return MINOR_UNITS.get(currency);
    }
}
