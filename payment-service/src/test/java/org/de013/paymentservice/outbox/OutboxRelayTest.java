package org.de013.paymentservice.outbox;

import org.de013.paymentservice.entity.OutboxEvent;
import org.de013.paymentservice.entity.enums.OutboxStatus;
import org.de013.paymentservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxRelayTest {

    @Mock
    private OutboxEventRepository repo;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OutboxRelay outboxRelay;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void publishPending_WhenNoPendingEvents_ShouldDoNothing() {
        // Arrange
        when(repo.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(Collections.emptyList());

        // Act
        outboxRelay.publishPending();

        // Assert
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void publishPending_WhenEventsExist_AndPublishSucceeds_ShouldMarkAsPublished() {
        // Arrange
        OutboxEvent event1 = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .topic("topic-1")
                .aggregateId("id-1")
                .payload("payload-1")
                .status(OutboxStatus.PENDING)
                .build();

        OutboxEvent event2 = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .topic("topic-2")
                .aggregateId("id-2")
                .payload("payload-2")
                .status(OutboxStatus.PENDING)
                .build();

        List<OutboxEvent> events = Arrays.asList(event1, event2);
        when(repo.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)).thenReturn(events);
        
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        // Act
        outboxRelay.publishPending();

        // Assert
        verify(kafkaTemplate, times(1)).send("topic-1", "id-1", "payload-1");
        verify(kafkaTemplate, times(1)).send("topic-2", "id-2", "payload-2");

        assertEquals(OutboxStatus.PUBLISHED, event1.getStatus());
        assertNotNull(event1.getPublishedAt());

        assertEquals(OutboxStatus.PUBLISHED, event2.getStatus());
        assertNotNull(event2.getPublishedAt());

        verify(repo, times(2)).save(any(OutboxEvent.class));
    }

    @Test
    void publishPending_WhenPublishFails_ShouldIncrementAttemptCountAndRemainPending() {
        // Arrange
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .topic("topic-1")
                .aggregateId("id-1")
                .payload("payload-1")
                .status(OutboxStatus.PENDING)
                .attemptCount(0)
                .build();

        when(repo.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(Collections.singletonList(event));

        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka down"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failedFuture);

        // Act
        outboxRelay.publishPending();

        // Assert
        verify(kafkaTemplate, times(1)).send("topic-1", "id-1", "payload-1");
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(1, event.getAttemptCount());
        assertNull(event.getPublishedAt());
        verify(repo, times(1)).save(event);
    }
}
