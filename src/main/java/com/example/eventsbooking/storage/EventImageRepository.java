package com.example.eventsbooking.storage;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The {@code event_images} table: one row per uploaded file. */
@Repository
public class EventImageRepository {

    private final JdbcClient jdbc;

    public EventImageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insertPending(String storageKey, Instant createdAt) {
        jdbc.sql("INSERT INTO event_images (storage_key, status, created_at) VALUES (:key, 'PENDING', :createdAt)")
                .param("key", storageKey).param("createdAt", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
                .update();
    }

    /** PENDING becomes ATTACHED and belongs to the event from now on. */
    public void attach(long eventId, Collection<String> storageKeys) {
        if (storageKeys.isEmpty()) {
            return;
        }
        jdbc.sql("""
                UPDATE event_images SET status = 'ATTACHED', event_id = :eventId, version = version + 1
                WHERE storage_key IN (:keys)
                """)
                .param("eventId", eventId).param("keys", storageKeys)
                .update();
    }

    /** A row as the cleanup sees it. */
    public record Row(long id, String storageKey) {
    }

    /** PENDING rows created before {@code cutoff}, in id order after {@code afterId}. */
    public List<Row> findPendingCreatedBefore(Instant cutoff, long afterId, int limit) {
        return jdbc.sql("""
                SELECT id, storage_key FROM event_images
                WHERE status = 'PENDING' AND created_at < :cutoff AND id > :afterId
                ORDER BY id LIMIT :limit
                """)
                .param("cutoff", OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC))
                .param("afterId", afterId).param("limit", limit)
                .query((rs, n) -> new Row(rs.getLong("id"), rs.getString("storage_key")))
                .list();
    }

    /** Removes the row only while it is still PENDING: an upload attached in the meantime is kept. */
    public boolean deleteIfPending(long id) {
        return jdbc.sql("DELETE FROM event_images WHERE id = :id AND status = 'PENDING'").param("id", id).update() == 1;
    }

    public List<Row> findAttached(long afterId, int limit) {
        return jdbc.sql("""
                SELECT id, storage_key FROM event_images WHERE status = 'ATTACHED' AND id > :afterId
                ORDER BY id LIMIT :limit
                """)
                .param("afterId", afterId).param("limit", limit)
                .query((rs, n) -> new Row(rs.getLong("id"), rs.getString("storage_key")))
                .list();
    }

    /** Which of the keys have a row (whatever its status). */
    public Set<String> existingKeys(Collection<String> storageKeys) {
        if (storageKeys.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("SELECT storage_key FROM event_images WHERE storage_key IN (:keys)")
                .param("keys", storageKeys).query(String.class).list());
    }
}
