package org.de013.paymentservice.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.paymentservice.entity.OutboxEvent;
import org.de013.paymentservice.entity.enums.OutboxStatus;
import org.de013.paymentservice.repository.OutboxEventRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private final OutboxEventRepository repo;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${outbox.relay.poll-ms:2000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = repo.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        if (batch.isEmpty()) {
            return;
        }
        log.debug("Found {} pending outbox events to publish", batch.size());
        for (OutboxEvent e : batch) {
            try {
                kafkaTemplate.send(e.getTopic(), e.getAggregateId(), e.getPayload()).get();
                e.setStatus(OutboxStatus.PUBLISHED);
                e.setPublishedAt(LocalDateTime.now());
                repo.save(e);
                log.debug("Successfully published outbox event: id={}", e.getId());
            } catch (Exception ex) {
                e.setAttemptCount(e.getAttemptCount() + 1);
                if (e.getAttemptCount() >= 10) {
                    e.setStatus(OutboxStatus.FAILED);
                }
                repo.save(e);
                log.warn("Outbox publish failed id={} attempt={}", e.getId(), e.getAttemptCount(), ex);
            }
        }
    }
}
