package org.de013.orderservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.de013.common.event.*;
import org.de013.orderservice.client.ProductCatalogClient;
import org.de013.orderservice.entity.Order;
import org.de013.orderservice.entity.OrderItem;
import org.de013.orderservice.entity.ProcessedEvent;
import org.de013.orderservice.repository.OrderRepository;
import org.de013.orderservice.repository.ProcessedEventRepository;
import org.de013.orderservice.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentEventListenerTest {

    private ObjectMapper objectMapper;

    @Mock
    private OrderService orderService;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductCatalogClient productCatalogClient;

    private PaymentEventListener paymentEventListener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        paymentEventListener = new PaymentEventListener(
                objectMapper,
                orderService,
                processedEventRepository,
                orderRepository,
                productCatalogClient
        );
    }

    @Test
    void testOnPaymentSucceeded_FirstTime_Success() throws Exception {
        // Arrange
        String eventId = "evt_123";
        PaymentSucceededPayload payload = PaymentSucceededPayload.builder()
                .orderId(1L)
                .paymentId(10L)
                .paymentNumber("PM-123")
                .userId("usr_abc")
                .amount(BigDecimal.TEN)
                .currency("USD")
                .build();

        EventEnvelope<PaymentSucceededPayload> envelope = EventEnvelope.<PaymentSucceededPayload>builder()
                .eventId(eventId)
                .eventType(EventTypes.PAYMENT_SUCCEEDED)
                .aggregateType("PAYMENT")
                .aggregateId("PM-123")
                .occurredAt(Instant.now())
                .payload(payload)
                .build();

        String message = objectMapper.writeValueAsString(envelope);

        when(processedEventRepository.existsById(eventId)).thenReturn(false);

        // Setup mock order and items
        Order order = new Order();
        order.setId(1L);
        OrderItem item = new OrderItem();
        item.setProductId("prod_xyz");
        item.setQuantity(3);
        order.addOrderItem(item);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        // Act
        paymentEventListener.onPaymentEvent(message);

        // Assert
        verify(orderService, times(1)).markOrderAsPaid(1L, 10L, "PM-123");
        verify(orderRepository, times(1)).findById(1L);
        verify(productCatalogClient, times(1)).fulfillStock("prod_xyz", 3);
        verify(processedEventRepository, times(1)).existsById(eventId);
        
        ArgumentCaptor<ProcessedEvent> processedEventCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository, times(1)).save(processedEventCaptor.capture());
        assertEquals(eventId, processedEventCaptor.getValue().getEventId());
    }

    @Test
    void testOnPaymentSucceeded_DuplicateEvent_Skipped() throws Exception {
        // Arrange
        String eventId = "evt_123";
        PaymentSucceededPayload payload = PaymentSucceededPayload.builder()
                .orderId(1L)
                .paymentId(10L)
                .paymentNumber("PM-123")
                .userId("usr_abc")
                .amount(BigDecimal.TEN)
                .currency("USD")
                .build();

        EventEnvelope<PaymentSucceededPayload> envelope = EventEnvelope.<PaymentSucceededPayload>builder()
                .eventId(eventId)
                .eventType(EventTypes.PAYMENT_SUCCEEDED)
                .aggregateType("PAYMENT")
                .aggregateId("PM-123")
                .occurredAt(Instant.now())
                .payload(payload)
                .build();

        String message = objectMapper.writeValueAsString(envelope);

        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        // Act
        paymentEventListener.onPaymentEvent(message);

        // Assert
        verify(orderService, never()).markOrderAsPaid(anyLong(), anyLong(), anyString());
        verify(orderRepository, never()).findById(anyLong());
        verify(productCatalogClient, never()).fulfillStock(anyString(), anyInt());
        verify(processedEventRepository, times(1)).existsById(eventId);
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void testOnPaymentFailed_FirstTime_Success() throws Exception {
        // Arrange
        String eventId = "evt_failed_123";
        PaymentFailedPayload payload = PaymentFailedPayload.builder()
                .orderId(2L)
                .paymentNumber("PM-456")
                .userId("usr_xyz")
                .failureReason("Insufficient funds")
                .build();

        EventEnvelope<PaymentFailedPayload> envelope = EventEnvelope.<PaymentFailedPayload>builder()
                .eventId(eventId)
                .eventType(EventTypes.PAYMENT_FAILED)
                .aggregateType("PAYMENT")
                .aggregateId("PM-456")
                .occurredAt(Instant.now())
                .payload(payload)
                .build();

        String message = objectMapper.writeValueAsString(envelope);

        when(processedEventRepository.existsById(eventId)).thenReturn(false);

        // Setup mock order and items
        Order order = new Order();
        order.setId(2L);
        OrderItem item = new OrderItem();
        item.setProductId("prod_abc");
        item.setQuantity(5);
        order.addOrderItem(item);

        when(orderRepository.findById(2L)).thenReturn(Optional.of(order));

        // Act
        paymentEventListener.onPaymentEvent(message);

        // Assert
        verify(orderService, times(1)).markOrderPaymentFailed(2L, "Insufficient funds");
        verify(orderRepository, times(1)).findById(2L);
        verify(productCatalogClient, times(1)).releaseStock("prod_abc", 5);
        verify(processedEventRepository, times(1)).existsById(eventId);
        
        ArgumentCaptor<ProcessedEvent> processedEventCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository, times(1)).save(processedEventCaptor.capture());
        assertEquals(eventId, processedEventCaptor.getValue().getEventId());
    }
}
