-- Reminders: which reminder of an event was already produced. Claiming the event (UPDATE ... WHERE ... IS NULL) is the
-- lock between several application instances.
ALTER TABLE events ADD COLUMN reminder_24h_sent_at TIMESTAMPTZ;
ALTER TABLE events ADD COLUMN reminder_2h_sent_at  TIMESTAMPTZ;

ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type
    CHECK (type IN ('BOOKING_CONFIRMED', 'SEAT_AVAILABLE', 'REMINDER_24H', 'REMINDER_2H'));

-- Profile. Existing accounts never confirmed their email, so email_verified starts false for everybody.
ALTER TABLE users ADD COLUMN name           VARCHAR(100);
ALTER TABLE users ADD COLUMN phone          VARCHAR(16);
ALTER TABLE users ADD COLUMN language       VARCHAR(2) NOT NULL DEFAULT 'EN';
ALTER TABLE users ADD COLUMN city           VARCHAR(100);
ALTER TABLE users ADD COLUMN email_verified BOOLEAN    NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD CONSTRAINT ck_users_language CHECK (language IN ('RU', 'PL', 'EN'));

-- A pending email change. Only the SHA-256 of the token is stored: a leaked table does not leak usable tokens.
CREATE TABLE email_verifications (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    new_email  VARCHAR(255) NOT NULL,
    token_hash VARCHAR(64)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_email_verifications_token UNIQUE (token_hash)
);
CREATE INDEX idx_email_verifications_user ON email_verifications (user_id, created_at DESC);

-- Per-user choice of channels for each notification type; a missing row means the default (both on).
CREATE TABLE notification_preferences (
    user_id BIGINT      NOT NULL,
    type    VARCHAR(32) NOT NULL,
    email   BOOLEAN     NOT NULL,
    push    BOOLEAN     NOT NULL,
    PRIMARY KEY (user_id, type),
    CONSTRAINT ck_notification_preferences_type
        CHECK (type IN ('BOOKING_CONFIRMED', 'SEAT_AVAILABLE', 'REMINDER_24H', 'REMINDER_2H'))
);
