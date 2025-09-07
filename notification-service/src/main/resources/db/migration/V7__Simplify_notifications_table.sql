-- Simplify notifications table for basic email/SMS functionality

-- Drop complex tables first
DROP TABLE IF EXISTS notification_deliveries CASCADE;
DROP TABLE IF EXISTS notification_preferences CASCADE;
DROP TABLE IF EXISTS notification_templates CASCADE;

-- Recreate simplified notifications table
DROP TABLE IF EXISTS notifications CASCADE;

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

-- Create indexes for performance
CREATE INDEX idx_notification_user_id ON notifications(user_id);
CREATE INDEX idx_notification_status ON notifications(status);
CREATE INDEX idx_notification_channel ON notifications(channel);
CREATE INDEX idx_notification_created_at ON notifications(created_at);
