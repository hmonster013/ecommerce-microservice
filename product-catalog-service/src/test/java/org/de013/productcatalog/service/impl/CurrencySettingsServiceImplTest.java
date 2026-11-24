package org.de013.productcatalog.service.impl;

import org.de013.productcatalog.config.CurrencyRegistry;
import org.de013.productcatalog.dto.currency.CurrencyRateRequest;
import org.de013.productcatalog.dto.currency.CurrencySettingsRequest;
import org.de013.productcatalog.entity.CurrencyExchangeRate;
import org.de013.productcatalog.entity.CurrencySettings;
import org.de013.productcatalog.exception.CurrencySettingsException;
import org.de013.productcatalog.repository.CurrencySettingsRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrencySettingsServiceImplTest {
    private final CurrencySettingsRepository repository = mock(CurrencySettingsRepository.class);
    private final CurrencySettingsServiceImpl service = new CurrencySettingsServiceImpl(repository, new CurrencyRegistry());

    @Test
    void getPublicRatesExcludesDisabledCurrencies() {
        CurrencySettings settings = settings(2L);
        settings.getRates().add(rate("USD", "1", true));
        settings.getRates().add(rate("VND", "25000", false));
        when(repository.findById(1L)).thenReturn(Optional.of(settings));

        var snapshot = service.getPublicRates();

        assertEquals("USD", snapshot.baseCurrency());
        assertEquals(1, snapshot.rates().size());
        assertEquals("USD", snapshot.rates().get(0).currency());
    }

    @Test
    void updateRejectsMissingUsdRate() {
        when(repository.findById(1L)).thenReturn(Optional.of(settings(1L)));

        CurrencySettingsException exception = assertThrows(CurrencySettingsException.class,
                () -> service.updateSettings(new CurrencySettingsRequest(1L,
                        List.of(new CurrencyRateRequest("VND", "25000", true)))));

        assertEquals("INVALID_CURRENCY_SETTINGS", exception.getErrorCode());
    }

    private CurrencySettings settings(long version) {
        CurrencySettings settings = new CurrencySettings();
        settings.setId(1L);
        settings.setBaseCurrency("USD");
        settings.setVersion(version);
        settings.setUpdatedAt(LocalDateTime.now());
        return settings;
    }

    @Test
    void invalidRateCarriesTheAffectedCurrency() {
        when(repository.findById(1L)).thenReturn(Optional.of(settings(1L)));
        CurrencySettingsException error = assertThrows(CurrencySettingsException.class,
                () -> service.updateSettings(new CurrencySettingsRequest(1L,
                        List.of(new CurrencyRateRequest("USD", "1", true), new CurrencyRateRequest("EUR", "0", true)))));
        assertEquals("EUR", error.getCurrency());
    }

    private CurrencyExchangeRate rate(String code, String value, boolean enabled) {
        CurrencyExchangeRate rate = new CurrencyExchangeRate();
        rate.setCurrencyCode(code);
        rate.setRate(new BigDecimal(value));
        rate.setEnabled(enabled);
        return rate;
    }
}
