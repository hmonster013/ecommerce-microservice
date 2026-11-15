package org.de013.notificationservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "notification.payment-email.events.enabled", havingValue = "true")
public class PaymentEmailWorkers {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final JavaMailSenderImpl sender;
    private final String from;
    private final int maxAttempts;

    public PaymentEmailWorkers(JdbcTemplate jdbc, TransactionTemplate transactions, @Qualifier("paymentEmailDltTemplate") KafkaTemplate<String, String> kafka,
                               ObjectMapper mapper, MailProperties mail, @Value("${notification.email.from:noreply@example.com}") String from,
                               @Value("${notification.payment-email.max-attempts:5}") int maxAttempts) {
        if (maxAttempts < 1) throw new IllegalArgumentException("max-attempts must be positive");
        this.jdbc = jdbc; this.transactions = transactions; this.kafka = kafka; this.mapper = mapper; this.from = from; this.maxAttempts = maxAttempts;
        sender = new JavaMailSenderImpl();
        sender.setHost(mail.getHost()); sender.setPort(mail.getPort() == null ? -1 : mail.getPort());
        sender.setUsername(mail.getUsername()); sender.setPassword(mail.getPassword());
        if (mail.getProtocol() != null) sender.setProtocol(mail.getProtocol());
        if (mail.getDefaultEncoding() != null) sender.setDefaultEncoding(mail.getDefaultEncoding().name());
        sender.getJavaMailProperties().putAll(mail.getProperties());
        for (String protocol : new String[]{"smtp", "smtps"}) {
            sender.getJavaMailProperties().setProperty("mail." + protocol + ".connectiontimeout", "10000");
            sender.getJavaMailProperties().setProperty("mail." + protocol + ".timeout", "10000");
            sender.getJavaMailProperties().setProperty("mail." + protocol + ".writetimeout", "10000");
        }
    }

    @Scheduled(fixedDelayString = "${notification.payment-email.poll-ms:1000}")
    public void deliver() { transactions.executeWithoutResult(status -> deliverOne()); }

    private void deliverOne() {
        var rows = jdbc.query("SELECT event_id,notification_id,recipient,subject,content,attempts FROM payment_email_request WHERE delivery_status='PENDING' AND next_attempt_at <= now() ORDER BY next_attempt_at,event_id FOR UPDATE SKIP LOCKED LIMIT 1",
                (r, n) -> new Delivery(r.getObject(1, UUID.class), r.getLong(2), r.getString(3), r.getString(4), r.getString(5), r.getInt(6)));
        if (rows.isEmpty()) return;
        Delivery row = rows.get(0);
        int attempts = row.attempts + 1;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from); message.setTo(row.recipient); message.setSubject(row.subject); message.setText(row.content);
            sender.send(message);
        } catch (RuntimeException failure) {
            boolean terminal = permanent(failure) || attempts >= maxAttempts;
            if (terminal) {
                jdbc.update("UPDATE payment_email_request SET delivery_status='FAILED',attempts=? WHERE event_id=? AND channel='EMAIL'", attempts, row.id);
                jdbc.update("UPDATE notifications SET status=CASE WHEN status='READ' THEN 'READ' ELSE 'FAILED' END, updated_at=CURRENT_TIMESTAMP WHERE id=?", row.historyId);
                jdbc.update("INSERT INTO payment_email_dlt_outbox (id,event_id,reason,attempts) VALUES (?,?,?,?)", UUID.randomUUID(), row.id, "DELIVERY_FAILED", attempts);
            } else {
                long delay = 5L << Math.min(attempts - 1, 16);
                jdbc.update("UPDATE payment_email_request SET attempts=?,next_attempt_at=? WHERE event_id=? AND channel='EMAIL'", attempts, java.sql.Timestamp.from(Instant.now().plusSeconds(delay)), row.id);
            }
            return;
        }
        jdbc.update("UPDATE payment_email_request SET delivery_status='SENT',attempts=? WHERE event_id=? AND channel='EMAIL'", attempts, row.id);
        jdbc.update("UPDATE notifications SET status=CASE WHEN status='READ' THEN 'READ' ELSE 'SENT' END, sent_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=?", row.historyId);
    }

    private static boolean permanent(Throwable failure) {
        var pending = new java.util.ArrayDeque<Throwable>();
        var visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        pending.add(failure);
        while (!pending.isEmpty()) {
            Throwable e = pending.removeFirst();
            if (!visited.add(e)) continue;
            if (e instanceof SendFailedException send && send.getInvalidAddresses() != null && send.getInvalidAddresses().length > 0) return true;
            if (e instanceof org.eclipse.angus.mail.smtp.SMTPAddressFailedException address && address.getReturnCode() >= 500 && address.getReturnCode() < 600) return true;
            if (e.getCause() != null) pending.add(e.getCause());
            if (e instanceof MessagingException mail && mail.getNextException() != null) pending.add(mail.getNextException());
            if (e instanceof MailSendException send) pending.addAll(send.getFailedMessages().values());
        }
        return false;
    }

    @Scheduled(fixedDelayString = "${notification.payment-email.dlt-poll-ms:1000}")
    public void relay() { transactions.executeWithoutResult(status -> relayOne()); }

    private void relayOne() {
        var rows = jdbc.query("SELECT o.id,o.event_id,o.reason,o.attempts,q.source_topic,q.source_partition,q.source_offset,q.raw_sanitized,q.raw_sha256,q.raw_length FROM payment_email_dlt_outbox o LEFT JOIN payment_email_quarantine q ON q.id=o.quarantine_id WHERE o.published_at IS NULL AND o.next_attempt_at <= now() ORDER BY o.next_attempt_at,o.id FOR UPDATE OF o SKIP LOCKED LIMIT 1", (r, n) -> {
            var envelope = new LinkedHashMap<String, Object>();
            UUID id = r.getObject(1, UUID.class), eventId = r.getObject(2, UUID.class);
            envelope.put("version", 1); envelope.put("dltId", id.toString()); envelope.put("reason", r.getString(3));
            envelope.put("eventId", eventId == null ? null : eventId.toString()); envelope.put("attempts", r.getInt(4));
            String topic = r.getString(5);
            if (topic != null) {
                envelope.put("sourceTopic", topic); envelope.put("sourcePartition", r.getInt(6)); envelope.put("sourceOffset", r.getLong(7));
                envelope.put("raw", r.getString(8)); envelope.put("rawSha256", r.getString(9)); envelope.put("rawLength", r.getLong(10));
            }
            String key = eventId != null ? eventId.toString() : topic + "-" + r.getInt(6) + "-" + r.getLong(7);
            return new Dlt(id, key, envelope);
        });
        if (rows.isEmpty()) return;
        Dlt row = rows.get(0);
        try {
            kafka.send("notification.payment-email.v1.dlt", row.key, mapper.writeValueAsString(row.envelope)).get(15, TimeUnit.SECONDS);
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            jdbc.update("UPDATE payment_email_dlt_outbox SET next_attempt_at=CURRENT_TIMESTAMP + interval '30 seconds' WHERE id=?", row.id);
            return;
        }
        jdbc.update("UPDATE payment_email_dlt_outbox SET published_at=CURRENT_TIMESTAMP WHERE id=?", row.id);
    }

    private record Delivery(UUID id, long historyId, String recipient, String subject, String content, int attempts) { }
    private record Dlt(UUID id, String key, LinkedHashMap<String, Object> envelope) { }
}
