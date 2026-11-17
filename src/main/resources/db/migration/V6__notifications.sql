-- event_id is the idempotency key of the BookingCreated message: a redelivered event cannot create a second row.
CREATE TABLE notifications (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id   UUID         NOT NULL,
    booking_id BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    message    VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_notifications_event_id UNIQUE (event_id)
);
