package org.de013.common.event;

public enum PaymentEventType {
    PaymentSucceeded("payment.succeeded.v1"),
    PaymentFailed("payment.failed.v1"),
    PaymentCanceled("payment.canceled.v1");

    private final String topic;

    PaymentEventType(String topic) {
        this.topic = topic;
    }

    public String topic() {
        return topic;
    }
}
