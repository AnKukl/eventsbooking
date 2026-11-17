-- Soft cancellation: a booking keeps its row and gets a status.
ALTER TABLE bookings ADD COLUMN status       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE bookings ADD COLUMN cancelled_at TIMESTAMPTZ;
ALTER TABLE bookings ADD CONSTRAINT ck_bookings_status CHECK (status IN ('ACTIVE', 'CANCELLED'));

-- One user may hold at most one ACTIVE booking per event; a cancelled one must not block re-booking.
ALTER TABLE bookings DROP CONSTRAINT uq_bookings_event_user;
CREATE UNIQUE INDEX uq_bookings_active_event_user ON bookings (event_id, user_id) WHERE status = 'ACTIVE';
