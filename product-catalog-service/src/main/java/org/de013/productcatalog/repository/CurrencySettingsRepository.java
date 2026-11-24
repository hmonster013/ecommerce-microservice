package org.de013.productcatalog.repository;

import org.de013.productcatalog.entity.CurrencySettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurrencySettingsRepository extends JpaRepository<CurrencySettings, Long> {
}
