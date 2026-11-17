ALTER TABLE payment_outbox ADD COLUMN traceparent VARCHAR(55), ADD COLUMN tracestate VARCHAR(512);
ALTER TABLE payment_notification_outbox ADD COLUMN traceparent VARCHAR(55), ADD COLUMN tracestate VARCHAR(512);
