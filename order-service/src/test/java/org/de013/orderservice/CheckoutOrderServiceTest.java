package org.de013.orderservice;

import org.de013.orderservice.dto.request.CreateOrderRequest;
import org.de013.orderservice.entity.CheckoutAttempt;
import org.de013.orderservice.entity.enums.OrderType;
import org.de013.orderservice.repository.*;
import org.de013.orderservice.service.OrderService;
import org.de013.orderservice.service.impl.CheckoutOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckoutOrderServiceTest {
    CheckoutAttemptRepository attempts = mock(CheckoutAttemptRepository.class);
    OrderRepository orders = mock(OrderRepository.class);
    OrderService ordersService = mock(OrderService.class);
    org.de013.orderservice.client.CartClient cart = mock(org.de013.orderservice.client.CartClient.class);
    CheckoutOrderService service = new CheckoutOrderService(attempts, orders, ordersService, cart, new com.fasterxml.jackson.databind.ObjectMapper(), mock(org.de013.orderservice.service.impl.AllocationRecovery.class));
    UUID key = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(null, 1L, OrderType.STANDARD, null, null, "CARD", "USD", null);

    @Test void legacyUncertainAttemptNeverReplaysOrderCreation() {
        when(attempts.findById(key.toString())).thenReturn(Optional.of(new CheckoutAttempt(key.toString(), "owner")));
        when(orders.findByCheckoutKeyAndUserId(key.toString(), "owner")).thenReturn(Optional.empty());
        assertThrows(org.de013.common.exception.ConflictException.class, () -> service.create(key, "owner", request));
        assertEquals("UNKNOWN", service.read(key, "owner").state());
        verifyNoInteractions(ordersService, cart);
    }
    @Test void blocksOtherOwner() {
        when(attempts.findById(key.toString())).thenReturn(Optional.of(new CheckoutAttempt(key.toString(), "owner")));
        assertThrows(AccessDeniedException.class, () -> service.read(key, "other"));
        verifyNoInteractions(orders);
    }
    @Test void rejectsNonUsdOrNonCard() {
        var invalid = new CreateOrderRequest(null, 1L, OrderType.STANDARD, null, null, "WALLET", "USD", null);
        assertThrows(IllegalArgumentException.class, () -> service.create(key, "owner", invalid));
        verifyNoInteractions(cart, ordersService);
    }
}
