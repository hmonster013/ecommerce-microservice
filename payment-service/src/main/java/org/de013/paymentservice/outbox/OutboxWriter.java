package org.de013.paymentservice.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.common.event.EventEnvelope;
import org.de013.paymentservice.entity.OutboxEvent;
import org.de013.paymentservice.entity.enums.OutboxStatus;
import org.de013.paymentservice.repository.OutboxEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)  // BẮT BUỘC gọi trong tx đang mở
    public void write(String topic, String aggregateType, String aggregateId,
                      String eventType, Object payload) {
        try {
            EventEnvelope<Object> env = EventEnvelope.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .occurredAt(Instant.now())
                .payload(payload)
                .build();

            OutboxEvent row = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .topic(topic)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .status(OutboxStatus.PENDING)
                .payload(objectMapper.writeValueAsString(env))
                .createdAt(LocalDateTime.now())
                .build();

            outboxEventRepository.save(row);
            log.debug("Outbox event written: id={}, type={}", row.getId(), eventType);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event payload", e);
            throw new RuntimeException("Failed to serialize outbox event payload", e);
        }
    }
}
