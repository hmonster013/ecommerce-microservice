package org.de013.notificationservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.de013.common.event.*;
import org.de013.notificationservice.entity.ProcessedEvent;
import org.de013.notificationservice.repository.ProcessedEventRepository;
import org.de013.notificationservice.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentEventListenerTest {

    private ObjectMapper objectMapper;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private PaymentEventListener paymentEventListener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        paymentEventListener = new PaymentEventListener(
                objectMapper,
                notificationService,
                processedEventRepository
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
                .receiptEmail("customer@example.com")
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

        // Act
        paymentEventListener.onPaymentEvent(message);

        // Assert
        verify(notificationService, times(1)).sendEmail(
                eq("usr_abc"),
                eq("customer@example.com"),
                eq("Thanh toán thành công cho Đơn hàng #1"),
                anyString()
        );
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
                .receiptEmail("customer@example.com")
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
        verify(notificationService, never()).sendEmail(anyString(), anyString(), anyString(), anyString());
        verify(processedEventRepository, times(1)).existsById(eventId);
        verify(processedEventRepository, never()).save(any());
    }
}
