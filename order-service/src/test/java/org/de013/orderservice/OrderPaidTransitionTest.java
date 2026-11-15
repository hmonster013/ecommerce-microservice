package org.de013.orderservice;

import org.de013.orderservice.client.CartServiceClient;
import org.de013.orderservice.client.ProductCatalogClient;
import org.de013.orderservice.entity.Order;
import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.mapper.OrderMapper;
import org.de013.orderservice.repository.OrderRepository;
import org.de013.orderservice.service.impl.OrderServiceImpl;
import org.de013.common.exception.ConflictException;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderPaidTransitionTest {
    OrderRepository repository = mock(OrderRepository.class);
    OrderServiceImpl service = new OrderServiceImpl(repository, mock(OrderMapper.class), mock(CartServiceClient.class), mock(ProductCatalogClient.class), mock(jakarta.persistence.EntityManager.class));

    @Test void duplicatePaymentSyncDoesNotRegressFulfillmentStatus() {
        var order = new Order(); order.setStatus(OrderStatus.SHIPPED);
        when(repository.lockForPayment(1L)).thenReturn(Optional.of(order));
        service.markOrderAsPaid(1L, 2L, "HOSTED-1");
        assertEquals(OrderStatus.SHIPPED, order.getStatus()); verify(repository, never()).save(any());
    }

    @Test void canceledOrderNeedsReconciliationNotAutomaticRevival() {
        var order = new Order(); order.setStatus(OrderStatus.CANCELLED);
        when(repository.lockForPayment(1L)).thenReturn(Optional.of(order));
        assertThrows(ConflictException.class, () -> service.markOrderAsPaid(1L, 2L, "HOSTED-1"));
        verify(repository, never()).save(any());
    }

    @Test void pendingOrderCanBecomePaidUnderLock() {
        var order = new Order(); order.setStatus(OrderStatus.PENDING);
        when(repository.lockForPayment(1L)).thenReturn(Optional.of(order));
        service.markOrderAsPaid(1L, 2L, "HOSTED-1");
        assertEquals(OrderStatus.PAID, order.getStatus()); verify(repository).save(order);
    }
}
