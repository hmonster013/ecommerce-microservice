package org.de013.orderservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

@Configuration
@EnableRabbit
public class RabbitMQConfig {

    @Value("${app.order.messaging.exchange:order.exchange}")
    private String orderExchangeName;

    @Value("${app.order.messaging.dead-letter-exchange:order.dlx}")
    private String deadLetterExchangeName;

    // Event routing keys
    public static final String RK_ORDER_CREATED = "order.created";
    public static final String RK_ORDER_STATUS_CHANGED = "order.status.changed";
    public static final String RK_ORDER_CANCELLED = "order.cancelled";
    public static final String RK_ORDER_SHIPPED = "order.shipped";
    public static final String RK_ORDER_DELIVERED = "order.delivered";

    // Queues
    public static final String Q_PAYMENT_CONFIRMATION = "order.payment.confirmation";
    public static final String Q_INVENTORY_UPDATE = "order.inventory.update";
    public static final String Q_SHIPPING_UPDATE = "order.shipping.update";
    public static final String Q_NOTIFICATION = "order.notification";

    public static final String Q_ORDER_PROCESSING = "order.processing";
    public static final String Q_EMAIL_NOTIFICATION = "order.email";
    public static final String Q_SMS_NOTIFICATION = "order.sms";
    public static final String Q_ANALYTICS = "order.analytics";
    public static final String Q_AUDIT = "order.audit";

    @Bean
    public TopicExchange orderExchange() {
        return ExchangeBuilder.topicExchange(orderExchangeName).durable(true).build();
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return ExchangeBuilder.topicExchange(deadLetterExchangeName).durable(true).build();
    }

    private Queue durableQueue(String name) {
        return QueueBuilder.durable(name)
                .withArgument("x-dead-letter-exchange", deadLetterExchangeName)
                .withArgument("x-dead-letter-routing-key", name + ".dlq")
                .build();
    }

    private Queue durableDlq(String name) {
        return QueueBuilder.durable(name + ".dlq").build();
    }

    // Declare queues
    @Bean public Queue paymentConfirmationQueue() { return durableQueue(Q_PAYMENT_CONFIRMATION); }
    @Bean public Queue paymentConfirmationDlq() { return durableDlq(Q_PAYMENT_CONFIRMATION); }

    @Bean public Queue inventoryUpdateQueue() { return durableQueue(Q_INVENTORY_UPDATE); }
    @Bean public Queue inventoryUpdateDlq() { return durableDlq(Q_INVENTORY_UPDATE); }

    @Bean public Queue shippingUpdateQueue() { return durableQueue(Q_SHIPPING_UPDATE); }
    @Bean public Queue shippingUpdateDlq() { return durableDlq(Q_SHIPPING_UPDATE); }

    @Bean public Queue notificationQueue() { return durableQueue(Q_NOTIFICATION); }
    @Bean public Queue notificationDlq() { return durableDlq(Q_NOTIFICATION); }

    @Bean public Queue rabbitOrderProcessingQueue() { return durableQueue(Q_ORDER_PROCESSING); }
    @Bean public Queue rabbitOrderProcessingDlq() { return durableDlq(Q_ORDER_PROCESSING); }

    @Bean public Queue emailQueue() { return durableQueue(Q_EMAIL_NOTIFICATION); }
    @Bean public Queue emailDlq() { return durableDlq(Q_EMAIL_NOTIFICATION); }

    @Bean public Queue smsQueue() { return durableQueue(Q_SMS_NOTIFICATION); }
    @Bean public Queue smsDlq() { return durableDlq(Q_SMS_NOTIFICATION); }

    @Bean public Queue analyticsQueue() { return durableQueue(Q_ANALYTICS); }
    @Bean public Queue analyticsDlq() { return durableDlq(Q_ANALYTICS); }

    @Bean public Queue auditQueue() { return durableQueue(Q_AUDIT); }
    @Bean public Queue auditDlq() { return durableDlq(Q_AUDIT); }

    // Bindings (example bindings; routing keys can be refined per consumer)
    @Bean
    public Binding bindPayment(Queue paymentConfirmationQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(paymentConfirmationQueue).to(orderExchange).with("payment.#");
    }

    @Bean
    public Binding bindInventory(Queue inventoryUpdateQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(inventoryUpdateQueue).to(orderExchange).with("inventory.#");
    }

    @Bean
    public Binding bindShipping(Queue shippingUpdateQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(shippingUpdateQueue).to(orderExchange).with("shipping.#");
    }

    @Bean
    public Binding bindNotification(Queue notificationQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(notificationQueue).to(orderExchange).with("notification.#");
    }

    @Bean
    public Binding bindOrderProcessing(Queue rabbitOrderProcessingQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(rabbitOrderProcessingQueue).to(orderExchange).with("order.processing");
    }

    @Bean
    public Binding bindEmail(Queue emailQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(emailQueue).to(orderExchange).with("email.#");
    }

    @Bean
    public Binding bindSms(Queue smsQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(smsQueue).to(orderExchange).with("sms.#");
    }

    @Bean
    public Binding bindAnalytics(Queue analyticsQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(analyticsQueue).to(orderExchange).with("analytics.#");
    }

    @Bean
    public Binding bindAudit(Queue auditQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(auditQueue).to(orderExchange).with("audit.#");
    }

    // DLQ bindings
    @Bean public Binding bindPaymentDlq(Queue paymentConfirmationDlq, TopicExchange deadLetterExchange) { return BindingBuilder.bind(paymentConfirmationDlq).to(deadLetterExchange).with(Q_PAYMENT_CONFIRMATION + ".dlq"); }
    @Bean public Binding bindInventoryDlq(Queue inventoryUpdateDlq, TopicExchange deadLetterExchange) { return BindingBuilder.bind(inventoryUpdateDlq).to(deadLetterExchange).with(Q_INVENTORY_UPDATE + ".dlq"); }
    @Bean public Binding bindShippingDlq(Queue shippingUpdateDlq, TopicExchange deadLetterExchange) { return BindingBuilder.bind(shippingUpdateDlq).to(deadLetterExchange).with(Q_SHIPPING_UPDATE + ".dlq"); }
    @Bean public Binding bindNotificationDlq(Queue notificationDlq, TopicExchange deadLetterExchange) { return BindingBuilder.bind(notificationDlq).to(deadLetterExchange).with(Q_NOTIFICATION + ".dlq"); }
    @Bean public Binding bindOrderProcessingDlq(Queue rabbitOrderProcessingDlq, TopicExchange deadLetterExchange) { return BindingBuilder.bind(rabbitOrderProcessingDlq).to(deadLetterExchange).with(Q_ORDER_PROCESSING + ".dlq"); }
    @Bean public Binding bindEmailDlq(Queue emailDlq, TopicExchange deadLetterExchange) { return BindingBuilder.bind(emailDlq).to(deadLetterExchange).with(Q_EMAIL_NOTIFICATION + ".dlq"); }
    @Bean public Binding bindSmsDlq(Queue smsDlq, TopicExchange deadLetterExchange) { return BindingBuilder.bind(smsDlq).to(deadLetterExchange).with(Q_SMS_NOTIFICATION + ".dlq"); }
    @Bean public Binding bindAnalyticsDlq(Queue analyticsDlq, TopicExchange deadLetterExchange) { return BindingBuilder.bind(analyticsDlq).to(deadLetterExchange).with(Q_ANALYTICS + ".dlq"); }
    @Bean public Binding bindAuditDlq(Queue auditDlq, TopicExchange deadLetterExchange) { return BindingBuilder.bind(auditDlq).to(deadLetterExchange).with(Q_AUDIT + ".dlq"); }

    // JSON converter
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        return rabbitTemplate;
    }
}

