package org.de013.productcatalog.service;

import org.de013.productcatalog.dto.currency.CurrencySettingsRequest;
import org.de013.productcatalog.dto.currency.CurrencySettingsResponse;
import org.de013.productcatalog.dto.currency.ExchangeRateSnapshot;

public interface CurrencySettingsService {
    ExchangeRateSnapshot getPublicRates();

    CurrencySettingsResponse getSettings();

    CurrencySettingsResponse updateSettings(CurrencySettingsRequest request);
}
