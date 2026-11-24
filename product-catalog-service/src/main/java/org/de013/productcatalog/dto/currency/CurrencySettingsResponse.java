package org.de013.productcatalog.dto.currency;

import java.time.LocalDateTime;
import java.util.List;

public record CurrencySettingsResponse(String baseCurrency, Long version, LocalDateTime updatedAt,
                                       String updatedBy, List<CurrencyRateResponse> rates) {
}
