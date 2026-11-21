-- Bookings become multi-seat and go through HOLD -> CONFIRMED; a cancellation may leave a refund pending.
ALTER TABLE bookings ADD COLUMN seats               INTEGER NOT NULL DEFAULT 1;
ALTER TABLE bookings ADD COLUMN hold_expires_at     TIMESTAMPTZ;
ALTER TABLE bookings ADD COLUMN cancellation_reason VARCHAR(32);
ALTER TABLE bookings ADD CONSTRAINT ck_bookings_seats CHECK (seats >= 1);

-- Every booking made so far is a real, confirmed one.
ALTER TABLE bookings DROP CONSTRAINT ck_bookings_status;
UPDATE bookings SET status = 'CONFIRMED' WHERE status = 'ACTIVE';
ALTER TABLE bookings ALTER COLUMN status SET DEFAULT 'HOLD';
ALTER TABLE bookings ADD CONSTRAINT ck_bookings_status
    CHECK (status IN ('HOLD', 'CONFIRMED', 'CANCELLED', 'REFUND_PENDING'));
ALTER TABLE bookings ADD CONSTRAINT ck_bookings_hold_has_expiry
    CHECK (status <> 'HOLD' OR hold_expires_at IS NOT NULL);

-- A user may have one active (HOLD or CONFIRMED) booking per event; a cancelled one does not block a new booking.
DROP INDEX uq_bookings_active_event_user;
CREATE UNIQUE INDEX uq_bookings_active_event_user ON bookings (event_id, user_id)
    WHERE status IN ('HOLD', 'CONFIRMED');

-- finds the holds to release without scanning the table
CREATE INDEX idx_bookings_hold_expiry ON bookings (hold_expires_at) WHERE status = 'HOLD';

-- A repeated POST /api/bookings with the same Idempotency-Key returns the same booking. The key is scoped to the user.
-- The row is created together with the booking in one transaction (booking_id is filled in at the end of it).
CREATE TABLE idempotency_keys (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    event_id        BIGINT       NOT NULL,
    seats           INTEGER      NOT NULL,
    booking_id      BIGINT REFERENCES bookings (id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_idempotency_user_key UNIQUE (user_id, idempotency_key)
);
