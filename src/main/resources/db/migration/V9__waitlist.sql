-- Queue for sold-out events. The queue order is (created_at, id); notified_at marks who was already told that a seat
-- is free, so each freed seat reaches exactly one person.
CREATE TABLE waitlist (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    event_id    BIGINT      NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    channel     VARCHAR(8)  NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    notified_at TIMESTAMPTZ,
    CONSTRAINT uq_waitlist_user_event UNIQUE (user_id, event_id),
    CONSTRAINT ck_waitlist_channel CHECK (channel IN ('EMAIL', 'PUSH'))
);

-- finds the head of an event's queue: the first entry that has not been notified yet
CREATE INDEX idx_waitlist_queue ON waitlist (event_id, created_at, id) WHERE notified_at IS NULL;
