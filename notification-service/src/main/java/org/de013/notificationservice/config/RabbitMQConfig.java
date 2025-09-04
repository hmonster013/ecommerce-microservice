package org.de013.notificationservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ Configuration for Notification Service
 */
@Configuration
public class RabbitMQConfig {

    // Queue names
    public static final String NOTIFICATION_QUEUE = "notification.delivery.queue";
    public static final String NOTIFICATION_RETRY_QUEUE = "notification.delivery.retry.queue";
    public static final String NOTIFICATION_DLQ = "notification.delivery.dlq";
    public static final String NOTIFICATION_PRIORITY_QUEUE = "notification.delivery.priority.queue";
    
    // Exchange names
    public static final String NOTIFICATION_EXCHANGE = "notification.exchange";
    public static final String NOTIFICATION_DLX = "notification.dlx";
    
    // Routing keys
    public static final String NOTIFICATION_ROUTING_KEY = "notification.delivery";
    public static final String NOTIFICATION_RETRY_ROUTING_KEY = "notification.retry";
    public static final String NOTIFICATION_DLQ_ROUTING_KEY = "notification.dlq";
    public static final String NOTIFICATION_PRIORITY_ROUTING_KEY = "notification.priority";

    @Value("${notification.queue.ttl:3600000}") // 1 hour default TTL
    private long messageTtl;

    @Value("${notification.queue.max-retry:3}")
    private int maxRetryAttempts;

    /**
     * Message converter for JSON serialization
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate with JSON converter
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    /**
     * Rabbit listener container factory
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        factory.setPrefetchCount(10);
        return factory;
    }

    // ========== Main Notification Exchange ==========

    /**
     * Main notification exchange
     */
    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(NOTIFICATION_EXCHANGE, true, false);
    }

    /**
     * Dead letter exchange
     */
    @Bean
    public TopicExchange notificationDlx() {
        return new TopicExchange(NOTIFICATION_DLX, true, false);
    }

    // ========== Main Delivery Queue ==========

    /**
     * Main notification delivery queue
     */
    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .withArgument("x-message-ttl", messageTtl)
                .withArgument("x-dead-letter-exchange", NOTIFICATION_DLX)
                .withArgument("x-dead-letter-routing-key", NOTIFICATION_DLQ_ROUTING_KEY)
                .build();
    }

    /**
     * Binding for main notification queue
     */
    @Bean
    public Binding notificationQueueBinding() {
        return BindingBuilder
                .bind(notificationQueue())
                .to(notificationExchange())
                .with(NOTIFICATION_ROUTING_KEY);
    }

    // ========== Priority Queue ==========

    /**
     * Priority notification queue for urgent/critical notifications
     */
    @Bean
    public Queue notificationPriorityQueue() {
        return QueueBuilder.durable(NOTIFICATION_PRIORITY_QUEUE)
                .withArgument("x-max-priority", 10)
                .withArgument("x-message-ttl", messageTtl)
                .withArgument("x-dead-letter-exchange", NOTIFICATION_DLX)
                .withArgument("x-dead-letter-routing-key", NOTIFICATION_DLQ_ROUTING_KEY)
                .build();
    }

    /**
     * Binding for priority notification queue
     */
    @Bean
    public Binding notificationPriorityQueueBinding() {
        return BindingBuilder
                .bind(notificationPriorityQueue())
                .to(notificationExchange())
                .with(NOTIFICATION_PRIORITY_ROUTING_KEY);
    }

    // ========== Retry Queue ==========

    /**
     * Retry queue with delayed message processing
     */
    @Bean
    public Queue notificationRetryQueue() {
        return QueueBuilder.durable(NOTIFICATION_RETRY_QUEUE)
                .withArgument("x-message-ttl", 60000) // 1 minute delay
                .withArgument("x-dead-letter-exchange", NOTIFICATION_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", NOTIFICATION_ROUTING_KEY)
                .build();
    }

    /**
     * Binding for retry queue
     */
    @Bean
    public Binding notificationRetryQueueBinding() {
        return BindingBuilder
                .bind(notificationRetryQueue())
                .to(notificationExchange())
                .with(NOTIFICATION_RETRY_ROUTING_KEY);
    }

    // ========== Dead Letter Queue ==========

    /**
     * Dead letter queue for permanently failed notifications
     */
    @Bean
    public Queue notificationDlq() {
        return QueueBuilder.durable(NOTIFICATION_DLQ).build();
    }

    /**
     * Binding for dead letter queue
     */
    @Bean
    public Binding notificationDlqBinding() {
        return BindingBuilder
                .bind(notificationDlq())
                .to(notificationDlx())
                .with(NOTIFICATION_DLQ_ROUTING_KEY);
    }

    // ========== Channel-specific Queues ==========

    /**
     * Email notification queue
     */
    @Bean
    public Queue emailNotificationQueue() {
        return QueueBuilder.durable("notification.email.queue")
                .withArgument("x-message-ttl", messageTtl)
                .withArgument("x-dead-letter-exchange", NOTIFICATION_DLX)
                .withArgument("x-dead-letter-routing-key", NOTIFICATION_DLQ_ROUTING_KEY)
                .build();
    }

    /**
     * SMS notification queue
     */
    @Bean
    public Queue smsNotificationQueue() {
        return QueueBuilder.durable("notification.sms.queue")
                .withArgument("x-message-ttl", messageTtl)
                .withArgument("x-dead-letter-exchange", NOTIFICATION_DLX)
                .withArgument("x-dead-letter-routing-key", NOTIFICATION_DLQ_ROUTING_KEY)
                .build();
    }

    /**
     * Push notification queue
     */
    @Bean
    public Queue pushNotificationQueue() {
        return QueueBuilder.durable("notification.push.queue")
                .withArgument("x-message-ttl", messageTtl)
                .withArgument("x-dead-letter-exchange", NOTIFICATION_DLX)
                .withArgument("x-dead-letter-routing-key", NOTIFICATION_DLQ_ROUTING_KEY)
                .build();
    }

    /**
     * In-app notification queue
     */
    @Bean
    public Queue inAppNotificationQueue() {
        return QueueBuilder.durable("notification.inapp.queue")
                .withArgument("x-message-ttl", messageTtl)
                .withArgument("x-dead-letter-exchange", NOTIFICATION_DLX)
                .withArgument("x-dead-letter-routing-key", NOTIFICATION_DLQ_ROUTING_KEY)
                .build();
    }

    // ========== Channel Queue Bindings ==========

    @Bean
    public Binding emailQueueBinding() {
        return BindingBuilder
                .bind(emailNotificationQueue())
                .to(notificationExchange())
                .with("notification.email");
    }

    @Bean
    public Binding smsQueueBinding() {
        return BindingBuilder
                .bind(smsNotificationQueue())
                .to(notificationExchange())
                .with("notification.sms");
    }

    @Bean
    public Binding pushQueueBinding() {
        return BindingBuilder
                .bind(pushNotificationQueue())
                .to(notificationExchange())
                .with("notification.push");
    }

    @Bean
    public Binding inAppQueueBinding() {
        return BindingBuilder
                .bind(inAppNotificationQueue())
                .to(notificationExchange())
                .with("notification.inapp");
    }
}
