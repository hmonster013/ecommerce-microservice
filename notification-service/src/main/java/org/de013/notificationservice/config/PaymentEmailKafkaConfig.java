package org.de013.notificationservice.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.de013.notificationservice.service.PaymentEmailIngestion;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;
import org.apache.kafka.clients.consumer.ConsumerRecord;

@Configuration
@EnableKafka
@EnableScheduling
@ConditionalOnProperty(name = "notification.payment-email.events.enabled", havingValue = "true")
public class PaymentEmailKafkaConfig {
    @Bean
    DefaultKafkaProducerFactory<String, String> paymentEmailDltProducerFactory(KafkaProperties properties) {
        var config = properties.buildProducerProperties();
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000);
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10000);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    KafkaTemplate<String, String> paymentEmailDltTemplate(@Qualifier("paymentEmailDltProducerFactory") DefaultKafkaProducerFactory<String, String> factory) {
        return new KafkaTemplate<>(factory);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, byte[]> paymentEmailListenerFactory(KafkaProperties properties) {
        var config = properties.buildConsumerProperties();
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "notification-payment-email-v1");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        var factory = new ConcurrentKafkaListenerContainerFactory<String, byte[]>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(config));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        var errors = new DefaultErrorHandler(new FixedBackOff(5000L, FixedBackOff.UNLIMITED_ATTEMPTS));
        errors.setClassifications(java.util.Map.of(), true);
        factory.setCommonErrorHandler(errors);
        return factory;
    }

    @Bean PaymentEmailListener paymentEmailListener(PaymentEmailIngestion ingestion) { return new PaymentEmailListener(ingestion); }

    public static class PaymentEmailListener {
        private final PaymentEmailIngestion ingestion;
        PaymentEmailListener(PaymentEmailIngestion ingestion) { this.ingestion = ingestion; }
        @KafkaListener(topics = "notification.payment-email.v1", containerFactory = "paymentEmailListenerFactory")
        public void accept(ConsumerRecord<String, byte[]> record) {
            ingestion.ingest(record.topic(), record.partition(), record.offset(), record.key(), record.value());
        }
    }
}
