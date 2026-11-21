-- A user's favorite events. user_id has no foreign key, like bookings.user_id (see docs/review.md); the event
-- reference cascades, so deleting an event removes its favorites.
CREATE TABLE favorites (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    event_id   BIGINT      NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_favorites_user_event UNIQUE (user_id, event_id)
);

-- serves "my favorites, newest first" (the unique index starts with user_id too, but cannot order by created_at)
CREATE INDEX idx_favorites_user_created ON favorites (user_id, created_at DESC);
