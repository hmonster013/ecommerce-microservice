package org.de013.orderservice;

import org.de013.orderservice.dto.request.CreateOrderRequest;
import org.de013.orderservice.entity.CheckoutAttempt;
import org.de013.orderservice.entity.Order;
import org.de013.orderservice.entity.enums.OrderType;
import org.de013.orderservice.repository.*;
import org.de013.orderservice.service.OrderService;
import org.de013.orderservice.service.impl.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckoutOrderServiceTest {
    CheckoutAttemptStore store = mock(CheckoutAttemptStore.class);
    CheckoutAttemptRepository attempts = mock(CheckoutAttemptRepository.class);
    OrderRepository orders = mock(OrderRepository.class);
    OrderService orderService = mock(OrderService.class);
    CheckoutOrderService service = new CheckoutOrderService(store, attempts, orders, orderService);
    UUID key = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(null, 1L, OrderType.STANDARD, null, null, "CARD", "USD", null);

    @Test void reservesBeforeCreatingAndOverridesBrowserIdentity() {
        service.create(key, "owner", request);
        var ordered = inOrder(store, orderService);
        ordered.verify(store).reserve(key.toString(), "owner");
        ordered.verify(orderService).createCheckoutOrder(request.withUserId("owner"), key.toString());
    }

    @Test void duplicateReadyDoesNotDeductStockAgain() {
        doThrow(new DuplicateKeyException("duplicate")).when(store).reserve(key.toString(), "owner");
        when(attempts.findById(key.toString())).thenReturn(Optional.of(new CheckoutAttempt(key.toString(), "owner")));
        var existing = new Order(); existing.setId(42L);
        when(orders.findByCheckoutKeyAndUserId(key.toString(), "owner")).thenReturn(Optional.of(existing));
        assertEquals("READY", service.create(key, "owner", request).state());
        verify(orderService).getOrderById(42L);
        verify(orderService, never()).createCheckoutOrder(any(), any());
    }

    @Test void uncertainReservationNeverReplaysOrderCreation() {
        doThrow(new DuplicateKeyException("duplicate")).when(store).reserve(key.toString(), "owner");
        when(attempts.findById(key.toString())).thenReturn(Optional.of(new CheckoutAttempt(key.toString(), "owner")));
        when(orders.findByCheckoutKeyAndUserId(key.toString(), "owner")).thenReturn(Optional.empty());
        assertEquals("UNKNOWN", service.create(key, "owner", request).state()); verifyNoInteractions(orderService);
    }

    @Test void blocksOtherOwner() {
        when(attempts.findById(key.toString())).thenReturn(Optional.of(new CheckoutAttempt(key.toString(), "owner")));
        assertThrows(AccessDeniedException.class, () -> service.read(key, "other")); verifyNoInteractions(orders);
    }

    @Test void rejectsNonUsdOrNonCard() {
        var invalid = new CreateOrderRequest(null, 1L, OrderType.STANDARD, null, null, "WALLET", "USD", null);
        assertThrows(IllegalArgumentException.class, () -> service.create(key, "owner", invalid)); verifyNoInteractions(store);
    }
}
