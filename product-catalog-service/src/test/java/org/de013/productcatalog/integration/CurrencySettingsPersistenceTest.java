package org.de013.productcatalog.integration;

import jakarta.persistence.EntityManager;
import org.de013.productcatalog.dto.currency.CurrencyRateRequest;
import org.de013.productcatalog.dto.currency.CurrencySettingsRequest;
import org.de013.productcatalog.entity.CurrencyExchangeRate;
import org.de013.productcatalog.entity.CurrencySettings;
import org.de013.productcatalog.repository.CurrencySettingsRepository;
import org.de013.productcatalog.service.CurrencySettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CurrencySettingsPersistenceTest {
    @Autowired private CurrencySettingsRepository repository;
    @Autowired private CurrencySettingsService service;
    @Autowired private EntityManager entityManager;

    @Test
    void repeatedSnapshotUpdatesRetainExistingRatesAndRemoveOmittedCurrencies() {
        CurrencySettings settings = new CurrencySettings();
        settings.setId(1L);
        settings.setBaseCurrency("USD");
        settings.setUpdatedAt(LocalDateTime.now());
        CurrencyExchangeRate usd = new CurrencyExchangeRate();
        usd.setCurrencyCode("USD");
        usd.setRate(BigDecimal.ONE);
        usd.setEnabled(true);
        usd.setSettings(settings);
        settings.getRates().add(usd);
        settings = repository.saveAndFlush(settings);
        long initialVersion = settings.getVersion();

        var first = service.updateSettings(new CurrencySettingsRequest(initialVersion, List.of(
                new CurrencyRateRequest("USD", "1", true), new CurrencyRateRequest("VND", "25000", true))));
        assertTrue(first.version() > initialVersion);
        entityManager.clear();
        assertEquals(2, service.getPublicRates().rates().size());

        var second = service.updateSettings(new CurrencySettingsRequest(first.version(), List.of(
                new CurrencyRateRequest("USD", "1", true), new CurrencyRateRequest("VND", "26000", false))));
        entityManager.clear();
        assertEquals(1, service.getPublicRates().rates().size());
        assertEquals(0, new BigDecimal("26000").compareTo(new BigDecimal(service.getSettings().rates().stream()
                .filter(rate -> rate.currency().equals("VND")).findFirst().orElseThrow().rate())));

        service.updateSettings(new CurrencySettingsRequest(second.version(), List.of(
                new CurrencyRateRequest("USD", "1", true))));
        entityManager.clear();
        assertEquals(1, service.getSettings().rates().size());
        assertEquals(1L, entityManager.createQuery("select count(r) from CurrencyExchangeRate r", Long.class).getSingleResult());
    }
}
