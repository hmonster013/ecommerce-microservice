-- Create notifications table for basic email/SMS functionality

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    recipient VARCHAR(500) NOT NULL,
    subject VARCHAR(500),
    content TEXT NOT NULL,
    sent_at TIMESTAMP,
    read_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes are managed by JPA @Index annotations in Notification entity
-- See: org.de013.notificationservice.entity.Notification

