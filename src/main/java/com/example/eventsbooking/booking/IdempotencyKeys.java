package com.example.eventsbooking.booking;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Idempotency-Key bookkeeping, always inside the booking transaction. {@link #claim} is a single INSERT ... ON CONFLICT
 * DO NOTHING: when another request holds the same key and has not committed yet, PostgreSQL makes this one wait for the
 * outcome, so two parallel requests with one key can never both create a booking.
 */
@Repository
public class IdempotencyKeys {

    /** What the first request with a key asked for and the booking it produced. */
    public record Entry(long eventId, int seats, long bookingId) {
    }

    private final JdbcClient jdbc;
    private final java.time.Clock clock;

    public IdempotencyKeys(JdbcClient jdbc, java.time.Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** True if the key was free and is now taken by this request; false if an earlier request already used it. */
    public boolean claim(long userId, String key, long eventId, int seats) {
        return jdbc.sql("""
                INSERT INTO idempotency_keys (user_id, idempotency_key, event_id, seats, created_at)
                VALUES (:userId, :key, :eventId, :seats, :createdAt)
                ON CONFLICT (user_id, idempotency_key) DO NOTHING
                """)
                .param("userId", userId).param("key", key).param("eventId", eventId).param("seats", seats)
                .param("createdAt", java.time.OffsetDateTime.ofInstant(clock.instant(),
                        java.time.ZoneOffset.UTC))
                .update() == 1;
    }

    public void attach(long userId, String key, long bookingId) {
        jdbc.sql("UPDATE idempotency_keys SET booking_id = :bookingId WHERE user_id = :userId AND idempotency_key = :key")
                .param("bookingId", bookingId).param("userId", userId).param("key", key)
                .update();
    }

    public Optional<Entry> find(long userId, String key) {
        return jdbc.sql("""
                SELECT event_id, seats, booking_id FROM idempotency_keys
                WHERE user_id = :userId AND idempotency_key = :key AND booking_id IS NOT NULL
                """)
                .param("userId", userId).param("key", key)
                .query((rs, row) -> new Entry(rs.getLong("event_id"), rs.getInt("seats"), rs.getLong("booking_id")))
                .optional();
    }
}
