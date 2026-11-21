-- Organizer cabinet. Events that exist already were created through the public API and stay published.
ALTER TABLE events ADD COLUMN status           VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED';
ALTER TABLE events ADD CONSTRAINT ck_events_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED'));

ALTER TABLE events ADD COLUMN venue            VARCHAR(200);
ALTER TABLE events ADD COLUMN price            NUMERIC(10, 2);
ALTER TABLE events ADD CONSTRAINT ck_events_price CHECK (price IS NULL OR price >= 0);

ALTER TABLE events ADD COLUMN waitlist_enabled BOOLEAN     NOT NULL DEFAULT TRUE;

-- The version an organizer edits against. Deliberately not the optimistic-lock "version" column: that one changes with
-- every booking, which would make every open edit form stale as soon as somebody books.
ALTER TABLE events ADD COLUMN revision         BIGINT      NOT NULL DEFAULT 0;

-- the public catalog and the organizer's own lists
CREATE INDEX idx_events_status_starts ON events (status, starts_at);
