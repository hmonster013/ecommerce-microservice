-- Run as one transaction after replacing the psql variables with reviewed values.
-- Existing DLT records remain immutable; this never consumes the DLT.
BEGIN;
WITH eligible AS (
    UPDATE payment_email_request
       SET delivery_status = 'PENDING', attempts = 0, next_attempt_at = CURRENT_TIMESTAMP
     WHERE event_id = :'event_id'::uuid AND channel = 'EMAIL' AND delivery_status = 'FAILED'
       AND length(trim(:'operator_name')) > 0 AND length(trim(:'reason')) > 0
    RETURNING event_id, notification_id
), audit AS (
    INSERT INTO payment_email_replay_audit(event_id, operator_name, reason)
    SELECT event_id, :'operator_name', :'reason' FROM eligible
    RETURNING event_id
)
UPDATE notifications n
   SET status = CASE WHEN n.status = 'READ' THEN 'READ' ELSE 'PENDING' END,
       updated_at = CURRENT_TIMESTAMP
  FROM eligible e JOIN audit a ON a.event_id = e.event_id
 WHERE n.id = e.notification_id;
COMMIT;
