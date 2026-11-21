-- Idempotency keys live for a limited time (app.idempotency.ttl); a periodic job removes the expired ones.
ALTER TABLE idempotency_keys ADD COLUMN expires_at TIMESTAMPTZ;
-- keys that exist already get the default 24 hours counted from their creation
UPDATE idempotency_keys SET expires_at = created_at + INTERVAL '24 hours';
ALTER TABLE idempotency_keys ALTER COLUMN expires_at SET NOT NULL;

-- the cleanup job looks for expired rows without scanning the table
CREATE INDEX idx_idempotency_keys_expires_at ON idempotency_keys (expires_at);
