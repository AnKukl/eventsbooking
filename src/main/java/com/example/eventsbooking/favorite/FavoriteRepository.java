package com.example.eventsbooking.favorite;

import com.example.eventsbooking.event.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    /**
     * Idempotent insert decided by the unique constraint, so two concurrent requests cannot both insert. Returns 1 if
     * the row was created, 0 if it already existed or the event does not exist (the caller tells these apart).
     */
    @Modifying
    @Query(value = """
            INSERT INTO favorites (user_id, event_id, created_at)
            SELECT CAST(:userId AS bigint), e.id, CAST(:createdAt AS timestamptz) FROM events e WHERE e.id = :eventId
            ON CONFLICT (user_id, event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") long userId, @Param("eventId") long eventId,
            @Param("createdAt") Instant createdAt);

    @Modifying
    @Query("DELETE FROM Favorite f WHERE f.userId = :userId AND f.eventId = :eventId")
    int deleteByUserIdAndEventId(@Param("userId") long userId, @Param("eventId") long eventId);

    /** The user's favorite events, newest favorite first. The order is fixed: pass an unsorted Pageable. */
    @Query(value = """
            SELECT e.* FROM events e JOIN favorites f ON f.event_id = e.id
            WHERE f.user_id = :userId ORDER BY f.created_at DESC, f.id DESC
            """,
            countQuery = "SELECT count(*) FROM favorites WHERE user_id = :userId",
            nativeQuery = true)
    Page<Event> findEventsByUserId(@Param("userId") long userId, Pageable pageable);

    @Query("SELECT f.eventId FROM Favorite f WHERE f.userId = :userId AND f.eventId IN :eventIds")
    Set<Long> findFavoredEventIds(@Param("userId") long userId, @Param("eventIds") Collection<Long> eventIds);
}
