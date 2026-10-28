CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipient_account_id BIGINT NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    related_entity_type VARCHAR(50),
    related_entity_id BIGINT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_notifications_read_at CHECK ((is_read AND read_at IS NOT NULL) OR NOT is_read)
);

ALTER TABLE notifications ADD COLUMN IF NOT EXISTS event_key VARCHAR(150);

CREATE INDEX IF NOT EXISTS ix_notifications_recipient_unread
    ON notifications (recipient_account_id, created_at DESC)
    WHERE NOT is_read;

CREATE INDEX IF NOT EXISTS ix_notifications_recipient_created
    ON notifications (recipient_account_id, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS ux_notifications_recipient_event_key
    ON notifications (recipient_account_id, event_key)
    WHERE event_key IS NOT NULL;
