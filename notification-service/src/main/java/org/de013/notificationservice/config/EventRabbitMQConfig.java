package org.de013.notificationservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ Configuration for Event-Driven Integration
 */
@Configuration
public class EventRabbitMQConfig {

    // ========== Event Exchange Names ==========
    public static final String ORDER_EVENTS_EXCHANGE = "order.events";
    public static final String PAYMENT_EVENTS_EXCHANGE = "payment.events";
    public static final String USER_EVENTS_EXCHANGE = "user.events";
    public static final String NOTIFICATION_EVENTS_EXCHANGE = "notification.events";
    public static final String ENGAGEMENT_EVENTS_EXCHANGE = "engagement.events";
    
    // ========== Event Queue Names ==========
    public static final String ORDER_NOTIFICATION_QUEUE = "notification.order.events";
    public static final String PAYMENT_NOTIFICATION_QUEUE = "notification.payment.events";
    public static final String USER_NOTIFICATION_QUEUE = "notification.user.events";
    public static final String NOTIFICATION_EVENTS_QUEUE = "notification.lifecycle.events";
    public static final String ENGAGEMENT_EVENTS_QUEUE = "user.engagement.events";
    
    // ========== Dead Letter Queues ==========
    public static final String ORDER_EVENTS_DLQ = "notification.order.events.dlq";
    public static final String PAYMENT_EVENTS_DLQ = "notification.payment.events.dlq";
    public static final String USER_EVENTS_DLQ = "notification.user.events.dlq";
    public static final String NOTIFICATION_EVENTS_DLQ = "notification.lifecycle.events.dlq";
    public static final String ENGAGEMENT_EVENTS_DLQ = "user.engagement.events.dlq";
    
    // ========== Dead Letter Exchange ==========
    public static final String EVENTS_DLX = "events.dlx";

    /**
     * Event listener container factory with error handling
     */
    @Bean("eventRabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory eventRabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(5);
        factory.setPrefetchCount(5);
        factory.setDefaultRequeueRejected(false); // Send to DLQ on failure
        return factory;
    }

    // ========== Event Exchanges ==========

    @Bean
    public TopicExchange orderEventsExchange() {
        return new TopicExchange(ORDER_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange(PAYMENT_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange userEventsExchange() {
        return new TopicExchange(USER_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange notificationEventsExchange() {
        return new TopicExchange(NOTIFICATION_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange engagementEventsExchange() {
        return new TopicExchange(ENGAGEMENT_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange eventsDlx() {
        return new TopicExchange(EVENTS_DLX, true, false);
    }

    // ========== Event Consumer Queues ==========

    @Bean
    public Queue orderNotificationQueue() {
        return QueueBuilder.durable(ORDER_NOTIFICATION_QUEUE)
                .withArgument("x-message-ttl", 3600000) // 1 hour
                .withArgument("x-dead-letter-exchange", EVENTS_DLX)
                .withArgument("x-dead-letter-routing-key", "order.events.dlq")
                .build();
    }

    @Bean
    public Queue paymentNotificationQueue() {
        return QueueBuilder.durable(PAYMENT_NOTIFICATION_QUEUE)
                .withArgument("x-message-ttl", 3600000)
                .withArgument("x-dead-letter-exchange", EVENTS_DLX)
                .withArgument("x-dead-letter-routing-key", "payment.events.dlq")
                .build();
    }

    @Bean
    public Queue userNotificationQueue() {
        return QueueBuilder.durable(USER_NOTIFICATION_QUEUE)
                .withArgument("x-message-ttl", 3600000)
                .withArgument("x-dead-letter-exchange", EVENTS_DLX)
                .withArgument("x-dead-letter-routing-key", "user.events.dlq")
                .build();
    }

    // ========== Event Publisher Queues ==========

    @Bean
    public Queue notificationEventsQueue() {
        return QueueBuilder.durable(NOTIFICATION_EVENTS_QUEUE)
                .withArgument("x-message-ttl", 3600000)
                .withArgument("x-dead-letter-exchange", EVENTS_DLX)
                .withArgument("x-dead-letter-routing-key", "notification.events.dlq")
                .build();
    }

    @Bean
    public Queue engagementEventsQueue() {
        return QueueBuilder.durable(ENGAGEMENT_EVENTS_QUEUE)
                .withArgument("x-message-ttl", 3600000)
                .withArgument("x-dead-letter-exchange", EVENTS_DLX)
                .withArgument("x-dead-letter-routing-key", "engagement.events.dlq")
                .build();
    }

    // ========== Dead Letter Queues ==========

    @Bean
    public Queue orderEventsDlq() {
        return QueueBuilder.durable(ORDER_EVENTS_DLQ).build();
    }

    @Bean
    public Queue paymentEventsDlq() {
        return QueueBuilder.durable(PAYMENT_EVENTS_DLQ).build();
    }

    @Bean
    public Queue userEventsDlq() {
        return QueueBuilder.durable(USER_EVENTS_DLQ).build();
    }

    @Bean
    public Queue notificationEventsDlq() {
        return QueueBuilder.durable(NOTIFICATION_EVENTS_DLQ).build();
    }

    @Bean
    public Queue engagementEventsDlq() {
        return QueueBuilder.durable(ENGAGEMENT_EVENTS_DLQ).build();
    }

    // ========== Consumer Queue Bindings ==========

    @Bean
    public Binding orderNotificationQueueBinding() {
        return BindingBuilder
                .bind(orderNotificationQueue())
                .to(orderEventsExchange())
                .with("order.#"); // Listen to all order events
    }

    @Bean
    public Binding paymentNotificationQueueBinding() {
        return BindingBuilder
                .bind(paymentNotificationQueue())
                .to(paymentEventsExchange())
                .with("payment.#"); // Listen to all payment events
    }

    @Bean
    public Binding userNotificationQueueBinding() {
        return BindingBuilder
                .bind(userNotificationQueue())
                .to(userEventsExchange())
                .with("user.#"); // Listen to all user events
    }

    // ========== Publisher Queue Bindings ==========

    @Bean
    public Binding notificationEventsQueueBinding() {
        return BindingBuilder
                .bind(notificationEventsQueue())
                .to(notificationEventsExchange())
                .with("notification.event.#");
    }

    @Bean
    public Binding engagementEventsQueueBinding() {
        return BindingBuilder
                .bind(engagementEventsQueue())
                .to(engagementEventsExchange())
                .with("user.engagement.#");
    }

    // ========== Dead Letter Queue Bindings ==========

    @Bean
    public Binding orderEventsDlqBinding() {
        return BindingBuilder
                .bind(orderEventsDlq())
                .to(eventsDlx())
                .with("order.events.dlq");
    }

    @Bean
    public Binding paymentEventsDlqBinding() {
        return BindingBuilder
                .bind(paymentEventsDlq())
                .to(eventsDlx())
                .with("payment.events.dlq");
    }

    @Bean
    public Binding userEventsDlqBinding() {
        return BindingBuilder
                .bind(userEventsDlq())
                .to(eventsDlx())
                .with("user.events.dlq");
    }

    @Bean
    public Binding notificationEventsDlqBinding() {
        return BindingBuilder
                .bind(notificationEventsDlq())
                .to(eventsDlx())
                .with("notification.events.dlq");
    }

    @Bean
    public Binding engagementEventsDlqBinding() {
        return BindingBuilder
                .bind(engagementEventsDlq())
                .to(eventsDlx())
                .with("engagement.events.dlq");
    }
}
