-- Preview fields of an event. Limits are enforced by the API and, as a last line of defence, by the database.

-- Old events get a safe preview text derived from their description (whitespace collapsed, cut to 160
-- characters; empty when there is no description). The lists start empty.
ALTER TABLE events ADD COLUMN short_description VARCHAR(160) NOT NULL DEFAULT '';
UPDATE events
SET short_description = left(btrim(regexp_replace(coalesce(description, ''), '\s+', ' ', 'g')), 160);

-- Ordered lists: (event_id, position) is the key, position is the index in the list.
CREATE TABLE event_audience_fit (
    event_id BIGINT       NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    position INTEGER      NOT NULL CHECK (position BETWEEN 0 AND 3),
    label    VARCHAR(100) NOT NULL,
    PRIMARY KEY (event_id, position)
);

CREATE TABLE event_audience_not_fit (
    event_id BIGINT       NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    position INTEGER      NOT NULL CHECK (position BETWEEN 0 AND 2),
    label    VARCHAR(100) NOT NULL,
    PRIMARY KEY (event_id, position)
);

-- position 0 is the cover image
CREATE TABLE event_media (
    event_id BIGINT        NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    position INTEGER       NOT NULL CHECK (position BETWEEN 0 AND 5),
    url      VARCHAR(2048) NOT NULL,
    alt      VARCHAR(200),
    PRIMARY KEY (event_id, position)
);
