-- Add digest_id column to notifications table

ALTER TABLE notifications 
ADD COLUMN digest_id BIGINT;

-- Add comment for the new column
COMMENT ON COLUMN notifications.digest_id IS 'ID of digest notification that includes this notification';

-- Add index for digest_id for better query performance
CREATE INDEX idx_notification_digest_id ON notifications(digest_id);
