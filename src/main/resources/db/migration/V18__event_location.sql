-- Where the event takes place on the map. Optional, but a point needs both coordinates.
ALTER TABLE events ADD COLUMN latitude  NUMERIC(9, 6);
ALTER TABLE events ADD COLUMN longitude NUMERIC(9, 6);
ALTER TABLE events ADD CONSTRAINT ck_events_location CHECK (
    (latitude IS NULL AND longitude IS NULL)
    OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180));
