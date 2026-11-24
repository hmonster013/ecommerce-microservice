package org.de013.orderservice.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.de013.orderservice.entity.Order;
import org.de013.orderservice.entity.enums.OrderStatus;
import org.de013.orderservice.repository.OrderRepository;
import org.de013.orderservice.service.OrderService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentEventProcessor {
    private final JdbcTemplate jdbc;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final ObjectMapper mapper;

    public PaymentEventProcessor(JdbcTemplate jdbc, OrderRepository orders, OrderService orderService, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.orders = orders;
        this.orderService = orderService;
        this.mapper = mapper;
    }

    private record Event(UUID id, Instant occurredAt, long paymentId, String paymentNumber, long orderId,
                         String provider, BigDecimal amount, String currency) {}

    @Transactional
    public void process(String payload, String key, String topic, int partition, long offset) {
        Event event;
        try {
            event = parse(payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException | java.time.DateTimeException e) {
            quarantine(payload, topic, partition, offset, "INVALID_EVENT");
            return;
        }
        if (!Long.toString(event.orderId()).equals(key)) {
            quarantine(payload, topic, partition, offset, "KEY_MISMATCH");
            return;
        }
        // ponytail: canonical hash covers v1 fields only; upgrade with the next schema version.
        String hash;
        try {
            hash = digest(mapper.writeValueAsString(List.of(event.id().toString(), event.occurredAt().toString(),
                    Long.toString(event.paymentId()), event.paymentNumber(), Long.toString(event.orderId()),
                    event.provider(), event.amount().stripTrailingZeros().toPlainString(), event.currency())));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        int claimed = jdbc.update("INSERT INTO payment_event_inbox(event_id,payload_hash,result) VALUES (?,?, 'PROCESSING') ON CONFLICT DO NOTHING", event.id(), hash);
        if (claimed == 0) {
            String prior = jdbc.queryForObject("SELECT payload_hash FROM payment_event_inbox WHERE event_id=?", String.class, event.id());
            if (!hash.equals(prior)) quarantine(payload, topic, partition, offset, "EVENT_ID_CONFLICT");
            return;
        }
        Order order = orders.lockForPayment(event.orderId()).orElse(null);
        if (order != null && order.getAllocationPhase() != null
                && Set.of("CLOSING", "RELEASE_PENDING", "CLOSED", "CLOSURE_HOLD").contains(order.getAllocationPhase())) {
            order.setAllocationPhase("CLOSURE_HOLD");
            order.setCheckoutCloseHoldReason("PAYMENT_SUCCESS_AFTER_CLOSURE");
            jdbc.update("UPDATE payment_event_inbox SET result='HOLD' WHERE event_id=?", event.id());
            return;
        }
        String reason = conflict(order, event);
        if (reason != null) {
            quarantine(payload, topic, partition, offset, reason);
            jdbc.update("UPDATE payment_event_inbox SET result='QUARANTINED' WHERE event_id=?", event.id());
            return;
        }
        orderService.markOrderAsPaid(event.orderId(), event.paymentId(), event.paymentNumber());
        jdbc.update("UPDATE payment_event_inbox SET result='ACCEPTED' WHERE event_id=?", event.id());
    }

    private String conflict(Order order, Event event) {
        if (order == null) return "UNKNOWN_ORDER";
        if (order.getTotalAmount() == null || order.getTotalAmount().getAmount() == null || order.getTotalAmount().getAmount().compareTo(event.amount()) != 0
                || !event.currency().equals(order.getTotalAmount().getCurrency())) return "AMOUNT_CURRENCY_MISMATCH";
        if (EnumSet.of(OrderStatus.PAID, OrderStatus.PROCESSING, OrderStatus.PREPARING, OrderStatus.SHIPPED,
                OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED, OrderStatus.COMPLETED,
                OrderStatus.PARTIALLY_SHIPPED, OrderStatus.PARTIALLY_DELIVERED).contains(order.getStatus())) {
            return order.getAcceptedPaymentId() != null && order.getAcceptedPaymentId().equals(event.paymentId())
                    && event.paymentNumber().equals(order.getAcceptedPaymentNumber()) ? null : "PAYMENT_RECONCILIATION";
        }
        if (order.getAllocationPhase() != null && !order.getAllocationPhase().equals("READY")) return "ALLOCATION_RECONCILIATION";
        if (!EnumSet.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PAYMENT_AUTHORIZED).contains(order.getStatus())) return "PAYMENT_RECONCILIATION";
        return null;
    }

    private Event parse(String payload) throws com.fasterxml.jackson.core.JsonProcessingException {
        if (payload == null) throw new IllegalArgumentException();
        JsonNode n = mapper.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(payload);
        if (n == null || !n.isObject() || !"PaymentSucceeded".equals(text(n, "eventType"))
                || !n.path("schemaVersion").isInt() || n.path("schemaVersion").intValue() != 1) throw new IllegalArgumentException();
        String time = text(n, "occurredAt");
        if (!time.endsWith("Z")) throw new IllegalArgumentException();
        UUID id = UUID.fromString(text(n, "eventId"));
        long payment = positiveLong(n, "paymentId"), order = positiveLong(n, "orderId");
        String number = text(n, "paymentNumber"), provider = text(n, "provider"), currency = text(n, "currency");
        JsonNode amountNode = n.path("amount");
        if (number.isBlank() || number.indexOf('\0') >= 0 || !Set.of("STRIPE", "VNPAY").contains(provider) || !currency.matches("[A-Z]{3}") || !amountNode.isNumber()) throw new IllegalArgumentException();
        BigDecimal amount = amountNode.decimalValue();
        if (amount.signum() <= 0) throw new IllegalArgumentException();
        return new Event(id, Instant.parse(time), payment, number, order, provider, amount, currency);
    }

    private static String text(JsonNode node, String name) {
        if (!node.path(name).isTextual()) throw new IllegalArgumentException();
        return node.path(name).textValue();
    }

    private static long positiveLong(JsonNode node, String name) {
        JsonNode value = node.path(name);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) throw new IllegalArgumentException();
        return value.longValue();
    }

    private void quarantine(String payload, String topic, int partition, long offset, String reason) {
        String raw = payload == null ? "" : payload;
        int length = raw.length();
        String safe = raw.replace("\0", "\\u0000");
        int end = safe.length() > 4096 && Character.isHighSurrogate(safe.charAt(4095)) && Character.isLowSurrogate(safe.charAt(4096)) ? 4095 : Math.min(safe.length(), 4096);
        String bounded = safe.substring(0, end);
        jdbc.update("INSERT INTO payment_event_quarantine(topic,partition_id,record_offset,reason,raw_payload,payload_length,payload_hash,truncated) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING",
                topic, partition, offset, reason, bounded, length, digest(raw), safe.length() > 4096);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
