-- The notifications table written by the Kafka consumer becomes a per-user feed (booking confirmations and
-- "a seat is free" messages). event_id stays the idempotency key of whatever produced the notification: the
-- BookingCreated message id, or the id of a seat release.
ALTER TABLE notifications RENAME COLUMN message TO body;
ALTER TABLE notifications ALTER COLUMN body TYPE VARCHAR(1000);

-- existing rows are all booking confirmations
ALTER TABLE notifications ADD COLUMN type  VARCHAR(32)  NOT NULL DEFAULT 'BOOKING_CONFIRMED';
ALTER TABLE notifications ADD COLUMN title VARCHAR(200) NOT NULL DEFAULT 'Booking confirmed';
ALTER TABLE notifications ALTER COLUMN type  DROP DEFAULT;
ALTER TABLE notifications ALTER COLUMN title DROP DEFAULT;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN ('BOOKING_CONFIRMED', 'SEAT_AVAILABLE'));

ALTER TABLE notifications ADD COLUMN read_at TIMESTAMPTZ;

-- the event the notification is about, so a client can link to it; no FK, the event may be deleted later
ALTER TABLE notifications ADD COLUMN target_event_id BIGINT;
UPDATE notifications n SET target_event_id = b.event_id FROM bookings b WHERE b.id = n.booking_id;

-- a waitlist notification has no booking
ALTER TABLE notifications ALTER COLUMN booking_id DROP NOT NULL;

CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC, id DESC);
