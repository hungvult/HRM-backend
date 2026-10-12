ALTER TABLE notifications ADD COLUMN IF NOT EXISTS event_key VARCHAR(150);

CREATE UNIQUE INDEX IF NOT EXISTS ux_notifications_recipient_event_key
    ON notifications (recipient_account_id, event_key)
    WHERE event_key IS NOT NULL;
