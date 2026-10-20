package org.de013.productcatalog.service.impl;

import lombok.RequiredArgsConstructor;
import org.de013.common.security.UserContextHolder;
import org.de013.productcatalog.config.CurrencyRegistry;
import org.de013.productcatalog.dto.currency.CurrencyRateRequest;
import org.de013.productcatalog.dto.currency.CurrencyRateResponse;
import org.de013.productcatalog.dto.currency.CurrencySettingsRequest;
import org.de013.productcatalog.dto.currency.CurrencySettingsResponse;
import org.de013.productcatalog.dto.currency.ExchangeRateSnapshot;
import org.de013.productcatalog.entity.CurrencyExchangeRate;
import org.de013.productcatalog.entity.CurrencySettings;
import org.de013.productcatalog.exception.CurrencySettingsException;
import org.de013.productcatalog.repository.CurrencySettingsRepository;
import org.de013.productcatalog.service.CurrencySettingsService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrencySettingsServiceImpl implements CurrencySettingsService {
    private static final String USD = "USD";
    private static final BigDecimal ONE = BigDecimal.ONE;
    private final CurrencySettingsRepository settingsRepository;
    private final CurrencyRegistry currencyRegistry;

    @Override
    public ExchangeRateSnapshot getPublicRates() {
        CurrencySettings settings = getSingleton();
        return new ExchangeRateSnapshot(settings.getBaseCurrency(), settings.getVersion(), settings.getUpdatedAt(),
                mapRates(settings.getRates().stream().filter(CurrencyExchangeRate::isEnabled).toList()));
    }

    @Override
    public CurrencySettingsResponse getSettings() {
        return toSettingsResponse(getSingleton());
    }

    @Override
    @Transactional
    public CurrencySettingsResponse updateSettings(CurrencySettingsRequest request) {
        CurrencySettings settings = getSingleton();
        if (!settings.getVersion().equals(request.version())) {
            throw CurrencySettingsException.conflict();
        }

        Map<String, CurrencyExchangeRate> newRates = new LinkedHashMap<>();
        Set<String> codes = new LinkedHashSet<>();
        for (CurrencyRateRequest item : request.rates()) {
            if (item == null || item.currency() == null || item.enabled() == null) {
                throw CurrencySettingsException.invalid("Currency, rate and enabled are required");
            }
            String currency = item.currency().trim().toUpperCase(Locale.ROOT);
            if (!currencyRegistry.isSupported(currency) || !codes.add(currency)) {
                throw CurrencySettingsException.invalid("Currency must be supported and unique: " + item.currency());
            }
            BigDecimal rate = parseRate(item.rate(), currency);
            if (USD.equals(currency) && (rate.compareTo(ONE) != 0 || !item.enabled())) {
                throw CurrencySettingsException.invalid("USD must have rate 1 and remain enabled");
            }
            CurrencyExchangeRate entity = new CurrencyExchangeRate();
            entity.setCurrencyCode(currency);
            entity.setRate(rate);
            entity.setEnabled(item.enabled());
            newRates.put(currency, entity);
        }
        if (!codes.contains(USD)) {
            throw CurrencySettingsException.invalid("USD must be included and enabled");
        }

        // Retain managed entities for existing codes; replacing them duplicates persistent identities.
        settings.getRates().removeIf(rate -> !codes.contains(rate.getCurrencyCode()));
        for (CurrencyExchangeRate existing : settings.getRates()) {
            CurrencyExchangeRate incoming = newRates.remove(existing.getCurrencyCode());
            existing.setRate(incoming.getRate());
            existing.setEnabled(incoming.isEnabled());
        }
        for (CurrencyExchangeRate added : newRates.values()) {
            added.setSettings(settings);
            settings.getRates().add(added);
        }
        settings.setUpdatedAt(LocalDateTime.now());
        settings.setUpdatedBy(UserContextHolder.getCurrentUserId());
        try {
            return toSettingsResponse(settingsRepository.saveAndFlush(settings));
        } catch (OptimisticLockingFailureException ex) {
            throw CurrencySettingsException.conflict();
        }
    }

    private CurrencySettings getSingleton() {
        return settingsRepository.findById(1L)
                .orElseThrow(() -> CurrencySettingsException.invalid("Currency settings are not initialized"));
    }

    private BigDecimal parseRate(String rawRate, String currency) {
        if (rawRate == null || !rawRate.matches("[0-9]{1,14}(\\.[0-9]{1,10})?")) {
            throw CurrencySettingsException.invalidRate("Invalid rate for " + currency, currency);
        }
        BigDecimal rate = new BigDecimal(rawRate);
        if (rate.signum() <= 0) {
            throw CurrencySettingsException.invalidRate("Rate must be greater than zero for " + currency, currency);
        }
        return rate;
    }

    private CurrencySettingsResponse toSettingsResponse(CurrencySettings settings) {
        return new CurrencySettingsResponse(settings.getBaseCurrency(), settings.getVersion(), settings.getUpdatedAt(),
                settings.getUpdatedBy(), mapRates(settings.getRates().stream().toList()));
    }

    private List<CurrencyRateResponse> mapRates(List<CurrencyExchangeRate> rates) {
        return rates.stream().sorted(Comparator.comparing(CurrencyExchangeRate::getCurrencyCode))
                .map(rate -> new CurrencyRateResponse(rate.getCurrencyCode(), rate.getRate().toPlainString(),
                        rate.isEnabled(), currencyRegistry.minorUnits(rate.getCurrencyCode())))
                .toList();
    }
}
