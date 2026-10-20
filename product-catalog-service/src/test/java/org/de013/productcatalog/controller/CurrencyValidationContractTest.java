package org.de013.productcatalog.controller;

import org.de013.productcatalog.exception.CurrencySettingsException;
import org.de013.productcatalog.exception.ProductCatalogExceptionHandler;
import org.de013.productcatalog.service.CurrencySettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CurrencyValidationContractTest {
    @Test
    void invalidRateIdentifiesTheCurrencyWithoutParsingAnEnglishMessage() throws Exception {
        CurrencySettingsService service = mock(CurrencySettingsService.class);
        when(service.updateSettings(any())).thenThrow(CurrencySettingsException.invalidRate("Invalid rate for EUR", "EUR"));
        MockMvcBuilders.standaloneSetup(new CurrencySettingsController(service))
                .setControllerAdvice(new ProductCatalogExceptionHandler()).build()
                .perform(put("/settings/currency").contentType(MediaType.APPLICATION_JSON).content("""
                        {"version":1,"rates":[{"currency":"USD","rate":"1","enabled":true},
                        {"currency":"EUR","rate":"0","enabled":true}]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENCY_SETTINGS"))
                .andExpect(jsonPath("$.metadata.currency").value("EUR"))
                .andExpect(jsonPath("$.metadata.field").value("rate"));
    }
}
