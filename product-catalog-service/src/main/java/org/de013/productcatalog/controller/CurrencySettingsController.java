package org.de013.productcatalog.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.de013.common.controller.BaseController;
import org.de013.common.dto.ApiResult;
import org.de013.productcatalog.dto.currency.CurrencySettingsRequest;
import org.de013.productcatalog.dto.currency.CurrencySettingsResponse;
import org.de013.productcatalog.dto.currency.ExchangeRateSnapshot;
import org.de013.productcatalog.service.CurrencySettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping
public class CurrencySettingsController extends BaseController {
    private final CurrencySettingsService currencySettingsService;

    @GetMapping("/exchange-rates")
    public ResponseEntity<ApiResult<ExchangeRateSnapshot>> getPublicRates() {
        return ok(currencySettingsService.getPublicRates());
    }

    @GetMapping("/settings/currency")
    public ResponseEntity<ApiResult<CurrencySettingsResponse>> getSettings() {
        return ok(currencySettingsService.getSettings());
    }

    @PutMapping("/settings/currency")
    public ResponseEntity<ApiResult<CurrencySettingsResponse>> updateSettings(
            @Valid @RequestBody CurrencySettingsRequest request) {
        return updated(currencySettingsService.updateSettings(request));
    }
}
