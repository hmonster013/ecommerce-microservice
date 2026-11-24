package org.de013.paymentservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.de013.common.exception.ConflictException;
import org.de013.paymentservice.client.OrderServiceClient;
import org.de013.paymentservice.client.UserServiceClient;
import org.de013.paymentservice.dto.external.OrderDto;
import org.de013.paymentservice.entity.enums.Currency;
import org.de013.paymentservice.service.validation.PaymentRequestValidator;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryReadyValidationTest {
    @Test void falseIsConflictAndTrueOrMissingRemainPayable() throws Exception {
        var orders = mock(OrderServiceClient.class);
        var validator = new PaymentRequestValidator(orders, mock(UserServiceClient.class));
        var mapper = new ObjectMapper();
        String base = "{\"id\":1,\"userId\":\"u\",\"status\":\"PENDING\",\"totalAmount\":{\"amount\":10,\"currency\":\"USD\"}";
        for (String suffix : new String[]{",\"inventoryReady\":true}", ",\"inventoryReady\":null}", "}"}) {
            when(orders.getOrderById(1L)).thenReturn(ResponseEntity.ok(mapper.readValue(base + suffix, OrderDto.class)));
            assertDoesNotThrow(() -> validator.validatePaymentAmount(1L, BigDecimal.TEN, Currency.USD, "u"));
        }
        when(orders.getOrderById(1L)).thenReturn(ResponseEntity.ok(mapper.readValue(base + ",\"inventoryReady\":false}", OrderDto.class)));
        assertThrows(ConflictException.class, () -> validator.validatePaymentAmount(1L, BigDecimal.TEN, Currency.USD, "u"));
    }
}
