package org.de013.orderservice.service.impl;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "order.payment-events.enabled", havingValue = "true")
public class PaymentEventListener {
    private final PaymentEventProcessor processor;

    public PaymentEventListener(PaymentEventProcessor processor) {
        this.processor = processor;
    }

    @KafkaListener(id = "order-payment-events", topics = "payment.succeeded.v1", groupId = "order-payment-v1",
            containerFactory = "paymentKafkaListenerContainerFactory")
    public void receive(ConsumerRecord<String, String> record) {
        processor.process(record.value(), record.key(), record.topic(), record.partition(), record.offset());
    }
}
