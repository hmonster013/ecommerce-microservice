-- Add missing columns to notification_preferences table

-- Rename language to language_code
ALTER TABLE notification_preferences RENAME COLUMN language TO language_code;

-- Add missing columns
ALTER TABLE notification_preferences 
ADD COLUMN marketing_opt_out BOOLEAN DEFAULT false,
ADD COLUMN snooze_until TIMESTAMP,
ADD COLUMN frequency_limit INTEGER,
ADD COLUMN digest_mode BOOLEAN DEFAULT false,
ADD COLUMN digest_frequency VARCHAR(20),
ADD COLUMN personalization_enabled BOOLEAN DEFAULT true,
ADD COLUMN ab_test_group VARCHAR(50),
ADD COLUMN custom_preferences JSONB DEFAULT '{}',
ADD COLUMN gdpr_consent BOOLEAN,
ADD COLUMN gdpr_consent_date TIMESTAMP,
ADD COLUMN can_spam_compliant BOOLEAN DEFAULT true,
ADD COLUMN last_engagement_date TIMESTAMP;

-- Add comments for new columns
COMMENT ON COLUMN notification_preferences.marketing_opt_out IS 'Opt-out from marketing notifications';
COMMENT ON COLUMN notification_preferences.snooze_until IS 'Temporary opt-out until this time';
COMMENT ON COLUMN notification_preferences.frequency_limit IS 'Max notifications per day (0 = unlimited)';
COMMENT ON COLUMN notification_preferences.digest_mode IS 'Receive notifications as digest';
COMMENT ON COLUMN notification_preferences.digest_frequency IS 'Digest frequency: DAILY, WEEKLY';
COMMENT ON COLUMN notification_preferences.personalization_enabled IS 'Enable content personalization';
COMMENT ON COLUMN notification_preferences.ab_test_group IS 'A/B testing group identifier';
COMMENT ON COLUMN notification_preferences.custom_preferences IS 'Custom user preferences';
COMMENT ON COLUMN notification_preferences.gdpr_consent IS 'GDPR consent status';
COMMENT ON COLUMN notification_preferences.gdpr_consent_date IS 'When GDPR consent was given';
COMMENT ON COLUMN notification_preferences.can_spam_compliant IS 'CAN-SPAM compliance status';
COMMENT ON COLUMN notification_preferences.last_engagement_date IS 'Last time user engaged with notifications';
