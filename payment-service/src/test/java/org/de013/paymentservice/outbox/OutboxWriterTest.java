package org.de013.paymentservice.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.de013.common.event.EventEnvelope;
import org.de013.paymentservice.entity.OutboxEvent;
import org.de013.paymentservice.entity.enums.OutboxStatus;
import org.de013.paymentservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OutboxWriterTest {

    @Autowired
    private OutboxWriter outboxWriter;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
    }

    @Test
    void write_ShouldSavePendingOutboxEvent_WhenCalledWithinTransaction() throws Exception {
        // Arrange
        String topic = "test-topic";
        String aggregateType = "TEST_AGGREGATE";
        String aggregateId = "agg-123";
        String eventType = "test.event";
        Map<String, String> payload = Map.of("key", "value");

        // Act
        outboxWriter.write(topic, aggregateType, aggregateId, eventType, payload);

        // Assert
        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertEquals(1, events.size());
        OutboxEvent event = events.get(0);

        assertNotNull(event.getId());
        assertEquals(topic, event.getTopic());
        assertEquals(aggregateType, event.getAggregateType());
        assertEquals(aggregateId, event.getAggregateId());
        assertEquals(eventType, event.getEventType());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(0, event.getAttemptCount());
        assertNotNull(event.getCreatedAt());
        assertNull(event.getPublishedAt());

        // Verify the payload contains EventEnvelope
        EventEnvelope<?> envelope = objectMapper.readValue(event.getPayload(), EventEnvelope.class);
        assertEquals(eventType, envelope.getEventType());
        assertEquals(aggregateType, envelope.getAggregateType());
        assertEquals(aggregateId, envelope.getAggregateId());
        assertNotNull(envelope.getEventId());
        assertNotNull(envelope.getOccurredAt());
        assertEquals(payload, envelope.getPayload());
    }
}
