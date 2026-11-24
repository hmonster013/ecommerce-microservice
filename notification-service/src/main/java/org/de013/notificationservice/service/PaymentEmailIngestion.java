package org.de013.notificationservice.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;

@Service
public class PaymentEmailIngestion {
    private static final int RAW_LIMIT = 4096;
    private static final int INPUT_LIMIT = 65536;
    private static final Set<String> FIELDS = Set.of("eventId", "eventType", "schemaVersion", "occurredAt", "paymentId", "paymentNumber", "orderId", "userId", "channel", "recipient", "subject", "content");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Validator validator;

    public PaymentEmailIngestion(JdbcTemplate jdbc, ObjectMapper mapper, Validator validator) {
        this.jdbc = jdbc;
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.validator = validator;
    }

    @Transactional
    public void ingest(String topic, int partition, long offset, String key, byte[] raw) {
        byte[] bytes = raw == null ? new byte[0] : raw;
        Event event;
        try {
            event = parse(key, bytes);
        } catch (IllegalArgumentException e) {
            quarantine(topic, partition, offset, null, "INVALID_RECORD", bytes);
            return;
        }
        // Serialize the absent-row case as well as existing rows, across service instances.
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?, 0)) IS NULL", Boolean.class, event.id.toString());
        var hashes = jdbc.query("SELECT canonical_hash FROM payment_email_request WHERE event_id=? AND channel='EMAIL'",
                (rs, n) -> rs.getString(1), event.id);
        if (!hashes.isEmpty()) {
            if (!hashes.get(0).equals(event.hash)) quarantine(topic, partition, offset, event.id, "EVENT_CONFLICT", bytes);
            return;
        }
        Long historyId = jdbc.queryForObject("INSERT INTO notifications (user_id,channel,status,recipient,subject,content) VALUES (?,'EMAIL','PENDING',?,?,?) RETURNING id",
                Long.class, event.userId, event.recipient, event.subject, event.content);
        jdbc.update("INSERT INTO payment_email_request (event_id,channel,canonical_hash,payment_id,payment_number,order_id,occurred_at,user_id,recipient,subject,content,notification_id) VALUES (?,'EMAIL',?,?,?,?,?,?,?,?,?,?)",
                event.id, event.hash, event.paymentId, event.paymentNumber, event.orderId, java.sql.Timestamp.from(event.occurredAt), event.userId, event.recipient, event.subject, event.content, historyId);
    }

    private Event parse(String key, byte[] bytes) {
        try {
            if (bytes.length == 0 || bytes.length > INPUT_LIMIT) throw new IllegalArgumentException();
            String raw = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonNode node = mapper.readTree(raw);
            if (node == null || !node.isObject() || node.size() != FIELDS.size()) throw new IllegalArgumentException();
            var names = node.fieldNames();
            while (names.hasNext()) if (!FIELDS.contains(names.next())) throw new IllegalArgumentException();
            String idText = text(node, "eventId", 36);
            UUID id = UUID.fromString(idText);
            if (!id.toString().equals(idText)) throw new IllegalArgumentException();
            if (!"PaymentEmailRequested".equals(text(node, "eventType", 64)) || !"EMAIL".equals(text(node, "channel", 20))) throw new IllegalArgumentException();
            JsonNode version = node.get("schemaVersion");
            if (version == null || !version.isIntegralNumber() || !version.canConvertToInt() || version.intValue() != 1) throw new IllegalArgumentException();
            long paymentId = positive(node, "paymentId");
            long orderId = positive(node, "orderId");
            if (!Long.toString(paymentId).equals(key)) throw new IllegalArgumentException();
            String time = text(node, "occurredAt", 64);
            if (!time.endsWith("Z")) throw new IllegalArgumentException();
            Instant instant = Instant.parse(time);
            String number = text(node, "paymentNumber", 16384);
            String user = text(node, "userId", 255);
            String recipient = text(node, "recipient", 254);
            String subject = text(node, "subject", 255);
            String content = text(node, "content", 16384);
            if (recipient.contains("\r") || recipient.contains("\n") || subject.contains("\r") || subject.contains("\n")) throw new IllegalArgumentException();
            int at = recipient.indexOf('@');
            if (at <= 0 || at != recipient.lastIndexOf('@') || at == recipient.length() - 1 || recipient.indexOf('<') >= 0 || recipient.indexOf('>') >= 0 || recipient.indexOf(',') >= 0 || recipient.codePoints().anyMatch(Character::isWhitespace) || !validator.validate(new Recipient(recipient)).isEmpty()) throw new IllegalArgumentException();
            // Length-prefixing avoids ambiguity and normalizes JSON ordering/escaping and instant notation.
            StringBuilder canonical = new StringBuilder();
            for (String value : new String[]{id.toString(), "PaymentEmailRequested", "1", instant.toString(), Long.toString(paymentId), number, Long.toString(orderId), user, "EMAIL", recipient, subject, content}) canonical.append(value.length()).append(':').append(value);
            return new Event(id, digest(canonical.toString().getBytes(StandardCharsets.UTF_8)), paymentId, number, orderId, instant, user, recipient, subject, content);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid payment email event");
        }
    }

    private static String text(JsonNode node, String field, int max) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) throw new IllegalArgumentException();
        String s = value.textValue();
        if (s.isBlank() || s.length() > max || s.indexOf('\0') >= 0 || s.codePoints().anyMatch(c -> c >= 0xd800 && c <= 0xdfff)) throw new IllegalArgumentException();
        return s;
    }

    private static long positive(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) throw new IllegalArgumentException();
        return value.longValue();
    }

    private void quarantine(String topic, int partition, long offset, UUID id, String reason, byte[] raw) {
        String sanitized = new String(raw, 0, Math.min(raw.length, RAW_LIMIT), StandardCharsets.UTF_8).replace('\0', '\ufffd');
        var ids = jdbc.query("INSERT INTO payment_email_quarantine (source_topic,source_partition,source_offset,event_id,reason,raw_sanitized,raw_sha256,raw_length) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT (source_topic,source_partition,source_offset) DO NOTHING RETURNING id", (rs, n) -> rs.getLong(1), topic, partition, offset, id, reason, sanitized, digest(raw), raw.length);
        if (!ids.isEmpty()) jdbc.update("INSERT INTO payment_email_dlt_outbox (id,quarantine_id,event_id,reason) VALUES (?,?,?,?)", UUID.randomUUID(), ids.get(0), id, reason);
    }

    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private record Event(UUID id, String hash, long paymentId, String paymentNumber, long orderId, Instant occurredAt, String userId, String recipient, String subject, String content) { }
    private record Recipient(@Email String value) { }
}
