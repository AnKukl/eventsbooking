-- Optimistic lock version and denormalised seat counter on events.
ALTER TABLE events ADD COLUMN version      BIGINT  NOT NULL DEFAULT 0;
ALTER TABLE events ADD COLUMN booked_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE events ADD CONSTRAINT ck_events_booked_within_capacity
    CHECK (booked_count >= 0 AND booked_count <= capacity);

CREATE TABLE bookings (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id   BIGINT      NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_bookings_event_user UNIQUE (event_id, user_id)
);
