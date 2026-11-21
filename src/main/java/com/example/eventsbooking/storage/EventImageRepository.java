package com.example.eventsbooking.storage;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;

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
}
