package org.de013.common.event;

public final class EventTypes {
    private EventTypes() {}
    public static final String PAYMENT_SUCCEEDED = "payment.succeeded";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String ORDER_PAID = "order.paid";
    public static final String ORDER_CREATED = "order.created";
}
