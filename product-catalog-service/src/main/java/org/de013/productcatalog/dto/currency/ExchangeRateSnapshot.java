package org.de013.productcatalog.dto.currency;

import java.time.LocalDateTime;
import java.util.List;

public record ExchangeRateSnapshot(String baseCurrency, Long version, LocalDateTime updatedAt,
                                   List<CurrencyRateResponse> rates) {
}
