package org.de013.productcatalog.dto.currency;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CurrencySettingsRequest(@NotNull Long version, @NotNull @Valid List<CurrencyRateRequest> rates) {
}
