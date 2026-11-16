CREATE TABLE events (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title        VARCHAR(200)  NOT NULL,
    description  VARCHAR(2000),
    starts_at    TIMESTAMPTZ   NOT NULL,
    capacity     INTEGER       NOT NULL,
    organizer_id BIGINT        NOT NULL,
    CONSTRAINT ck_events_capacity_positive CHECK (capacity > 0)
);

CREATE INDEX idx_events_organizer_id ON events (organizer_id);
CREATE INDEX idx_events_starts_at ON events (starts_at);
