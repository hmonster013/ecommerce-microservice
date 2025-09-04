-- Create indexes for notification service tables

-- Indexes for notification_templates table
CREATE INDEX idx_template_name ON notification_templates(name);
CREATE INDEX idx_template_type ON notification_templates(type);
CREATE INDEX idx_template_channel ON notification_templates(channel);
CREATE INDEX idx_template_language ON notification_templates(language);
CREATE INDEX idx_template_active ON notification_templates(active);
CREATE INDEX idx_template_version ON notification_templates(template_version);
CREATE INDEX idx_template_created_at ON notification_templates(created_at);

-- Unique constraint for template name, channel, and language
CREATE UNIQUE INDEX uk_template_name_channel_language 
ON notification_templates(name, channel, language) 
WHERE deleted = false;

-- Indexes for notifications table
CREATE INDEX idx_notification_user_id ON notifications(user_id);
CREATE INDEX idx_notification_type ON notifications(type);
CREATE INDEX idx_notification_status ON notifications(status);
CREATE INDEX idx_notification_channel ON notifications(channel);
CREATE INDEX idx_notification_priority ON notifications(priority);
CREATE INDEX idx_notification_scheduled_at ON notifications(scheduled_at);
CREATE INDEX idx_notification_created_at ON notifications(created_at);
CREATE INDEX idx_notification_sent_at ON notifications(sent_at);
CREATE INDEX idx_notification_delivered_at ON notifications(delivered_at);
CREATE INDEX idx_notification_expires_at ON notifications(expires_at);
CREATE INDEX idx_notification_next_retry_at ON notifications(next_retry_at);
CREATE INDEX idx_notification_correlation_id ON notifications(correlation_id);
CREATE INDEX idx_notification_external_id ON notifications(external_id);
CREATE INDEX idx_notification_reference ON notifications(reference_type, reference_id);
CREATE INDEX idx_notification_template_id ON notifications(template_id);

-- Composite indexes for common queries
CREATE INDEX idx_notification_user_status ON notifications(user_id, status) WHERE deleted = false;
CREATE INDEX idx_notification_user_type ON notifications(user_id, type) WHERE deleted = false;
CREATE INDEX idx_notification_user_channel ON notifications(user_id, channel) WHERE deleted = false;
CREATE INDEX idx_notification_status_priority ON notifications(status, priority) WHERE deleted = false;
CREATE INDEX idx_notification_ready_delivery ON notifications(status, scheduled_at, expires_at) WHERE deleted = false;

-- Indexes for notification_deliveries table
CREATE INDEX idx_delivery_notification_id ON notification_deliveries(notification_id);
CREATE INDEX idx_delivery_channel ON notification_deliveries(channel);
CREATE INDEX idx_delivery_status ON notification_deliveries(status);
CREATE INDEX idx_delivery_attempt_at ON notification_deliveries(attempted_at);
CREATE INDEX idx_delivery_delivered_at ON notification_deliveries(delivered_at);
CREATE INDEX idx_delivery_failed_at ON notification_deliveries(failed_at);
CREATE INDEX idx_delivery_next_attempt_at ON notification_deliveries(next_attempt_at);
CREATE INDEX idx_delivery_external_id ON notification_deliveries(external_id);
CREATE INDEX idx_delivery_provider_message_id ON notification_deliveries(provider_message_id);
CREATE INDEX idx_delivery_provider_name ON notification_deliveries(provider_name);
CREATE INDEX idx_delivery_created_at ON notification_deliveries(created_at);

-- Composite indexes for delivery queries
CREATE INDEX idx_delivery_notification_channel ON notification_deliveries(notification_id, channel) WHERE deleted = false;
CREATE INDEX idx_delivery_status_retry ON notification_deliveries(status, next_attempt_at, attempt_count) WHERE deleted = false;
CREATE INDEX idx_delivery_channel_status ON notification_deliveries(channel, status) WHERE deleted = false;

-- Indexes for notification_preferences table
CREATE INDEX idx_preference_user_id ON notification_preferences(user_id);
CREATE INDEX idx_preference_channel ON notification_preferences(channel);
CREATE INDEX idx_preference_type ON notification_preferences(type);
CREATE INDEX idx_preference_enabled ON notification_preferences(enabled);
CREATE INDEX idx_preference_global_opt_out ON notification_preferences(global_opt_out);
CREATE INDEX idx_preference_quiet_hours ON notification_preferences(quiet_hours_enabled);
CREATE INDEX idx_preference_timezone ON notification_preferences(timezone);
CREATE INDEX idx_preference_language ON notification_preferences(language);
CREATE INDEX idx_preference_created_at ON notification_preferences(created_at);

-- Unique constraint for user preferences
CREATE UNIQUE INDEX uk_preference_user_channel_type 
ON notification_preferences(user_id, channel, type) 
WHERE deleted = false;

-- Composite indexes for preference queries
CREATE INDEX idx_preference_user_enabled ON notification_preferences(user_id, enabled, global_opt_out) WHERE deleted = false;
CREATE INDEX idx_preference_channel_enabled ON notification_preferences(channel, enabled, global_opt_out) WHERE deleted = false;
CREATE INDEX idx_preference_type_enabled ON notification_preferences(type, enabled, global_opt_out) WHERE deleted = false;
