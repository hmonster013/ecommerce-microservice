package org.de013.productcatalog.dto.currency;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CurrencyRateRequest(@NotBlank String currency, @NotBlank String rate, @NotNull Boolean enabled) {
}
