package org.de013.orderservice.messaging.consumers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.de013.orderservice.entity.Order;
import org.de013.orderservice.repository.OrderRepository;
import org.de013.orderservice.service.integration.InventoryIntegrationService;
import org.de013.orderservice.service.integration.NotificationIntegrationService;
import org.de013.orderservice.service.integration.PaymentIntegrationService;
import org.de013.orderservice.service.integration.ShippingIntegrationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageConsumers {

    private final PaymentIntegrationService paymentIntegrationService;
    private final InventoryIntegrationService inventoryIntegrationService;
    private final ShippingIntegrationService shippingIntegrationService;
    private final NotificationIntegrationService notificationIntegrationService;
    private final OrderRepository orderRepository;

    @RabbitListener(queues = org.de013.orderservice.config.RabbitMQConfig.Q_PAYMENT_CONFIRMATION)
    public void onPaymentMessage(@Payload org.de013.orderservice.dto.message.OrderInboundMessages.PaymentConfirmationMessage msg,
                                 com.rabbitmq.client.Channel channel,
                                 @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {
        try {
            log.info("[PaymentConfirmationConsumer] Received: {}", msg);
            if (msg.getOrderId() != null && msg.getAmount() != null) {
                var order = orderRepository.findById(msg.getOrderId()).orElse(null);
                if (order != null) {
                    if ("REFUNDED".equalsIgnoreCase(msg.getStatus())) {
                        paymentIntegrationService.refundPayment(order, msg.getAmount(), "MQ refund");
                    } else if ("CAPTURED".equalsIgnoreCase(msg.getStatus())) {
                        paymentIntegrationService.capturePayment(order);
                    } else if ("AUTHORIZED".equalsIgnoreCase(msg.getStatus())) {
                        paymentIntegrationService.authorizePayment(order);
                    }
                }
            }
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("Payment consumer error", e);
            channel.basicNack(tag, false, false);
        }
    }

    @RabbitListener(queues = org.de013.orderservice.config.RabbitMQConfig.Q_INVENTORY_UPDATE)
    public void onInventoryUpdate(@Payload org.de013.orderservice.dto.message.OrderInboundMessages.InventoryUpdateMessage msg,
                                  com.rabbitmq.client.Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {
        try {
            log.info("[InventoryUpdateConsumer] Received: {}", msg);
            if (msg.getOrderId() != null && msg.getAction() != null) {
                var order = orderRepository.findById(msg.getOrderId()).orElse(null);
                if (order != null) {
                    if ("RESERVE".equalsIgnoreCase(msg.getAction())) {
                        inventoryIntegrationService.reserve(order);
                    } else if ("RELEASE".equalsIgnoreCase(msg.getAction())) {
                        inventoryIntegrationService.release(order);
                    }
                }
            }
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("Inventory consumer error", e);
            channel.basicNack(tag, false, false);
        }
    }

    @RabbitListener(queues = org.de013.orderservice.config.RabbitMQConfig.Q_SHIPPING_UPDATE)
    public void onShippingUpdate(@Payload org.de013.orderservice.dto.message.OrderInboundMessages.ShippingUpdateMessage msg,
                                 com.rabbitmq.client.Channel channel,
                                 @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {
        try {
            log.info("[ShippingUpdateConsumer] Received: {}", msg);
            if (msg.getOrderId() != null && msg.getStatus() != null) {
                var order = orderRepository.findById(msg.getOrderId()).orElse(null);
                if (order != null) {
                    if ("SHIPPED".equalsIgnoreCase(msg.getStatus()) && msg.getTrackingNumber() != null && msg.getCarrier() != null) {
                        shippingIntegrationService.createShipment(order);
                    }
                }
            }
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("Shipping consumer error", e);
            channel.basicNack(tag, false, false);
        }
    }

    @RabbitListener(queues = org.de013.orderservice.config.RabbitMQConfig.Q_NOTIFICATION)
    public void onNotification(@Payload org.de013.orderservice.dto.message.OrderInboundMessages.NotificationMessage msg,
                               com.rabbitmq.client.Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {
        try {
            log.info("[NotificationConsumer] Received: {}", msg);
            // Demo routing
            if ("EMAIL".equalsIgnoreCase(msg.getChannel())) {
                // Could call AsyncTaskPublisher.sendEmail
            } else if ("SMS".equalsIgnoreCase(msg.getChannel())) {
                // Could call AsyncTaskPublisher.sendSms
            }
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("Notification consumer error", e);
            channel.basicNack(tag, false, false);
        }
    }
}

