-- Every uploaded file has a row: PENDING from the upload until the file belongs to an event, then ATTACHED.
-- A PENDING row that stays for long is an upload that was never attached (the cleanup removes it).
CREATE TABLE event_images (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    storage_key VARCHAR(255) NOT NULL,
    event_id    BIGINT REFERENCES events (id) ON DELETE CASCADE,
    status      VARCHAR(16)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_event_images_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_event_images_status CHECK (status IN ('PENDING', 'ATTACHED'))
);

-- the orphan cleanup looks for old PENDING rows
CREATE INDEX idx_event_images_pending ON event_images (created_at) WHERE status = 'PENDING';
CREATE INDEX idx_event_images_event ON event_images (event_id);

-- Photos uploaded before this table existed are attached ones; without rows the file cleanup would take them for orphans.
INSERT INTO event_images (storage_key, event_id, status, created_at)
SELECT substring(url FROM '^/api/media/(.+)$'), event_id, 'ATTACHED', now()
FROM event_media
WHERE url LIKE '/api/media/%'
ON CONFLICT (storage_key) DO NOTHING;
