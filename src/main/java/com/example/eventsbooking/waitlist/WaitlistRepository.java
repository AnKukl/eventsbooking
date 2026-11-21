package com.example.eventsbooking.waitlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    /** Idempotent insert decided by the unique constraint: 1 if the entry was created, 0 if it already existed. */
    @Modifying
    @Query(value = """
            INSERT INTO waitlist (user_id, event_id, channel, created_at)
            VALUES (:userId, :eventId, :channel, :createdAt)
            ON CONFLICT (user_id, event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") long userId, @Param("eventId") long eventId,
            @Param("channel") String channel, @Param("createdAt") Instant createdAt);

    boolean existsByUserIdAndEventId(long userId, long eventId);

    /**
     * The head of the queue: the earliest entry not notified yet whose owner does not already hold a seat. The row is
     * locked and rows locked by a concurrent release are skipped, so two releases handled at the same time notify two
     * different people instead of the same one.
     */
    @Query(value = """
            SELECT w.* FROM waitlist w
            WHERE w.event_id = :eventId AND w.notified_at IS NULL
              AND NOT EXISTS (SELECT 1 FROM bookings b
                              WHERE b.event_id = w.event_id AND b.user_id = w.user_id AND b.status = 'ACTIVE')
            ORDER BY w.created_at, w.id
            LIMIT 1
            FOR UPDATE OF w SKIP LOCKED
            """, nativeQuery = true)
    Optional<WaitlistEntry> findNextToNotify(@Param("eventId") long eventId);

    @Modifying
    @Query("UPDATE WaitlistEntry w SET w.notifiedAt = :at WHERE w.id = :id")
    int markNotified(@Param("id") long id, @Param("at") Instant at);
}
