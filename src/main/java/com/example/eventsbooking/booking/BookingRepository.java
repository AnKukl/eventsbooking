package com.example.eventsbooking.booking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Page<Booking> findByUserId(long userId, Pageable pageable);

    boolean existsByEventIdAndUserIdAndStatusIn(long eventId, long userId, Collection<BookingStatus> statuses);

    long countByEventId(long eventId);

    /**
     * Holds whose time is up, locked for this transaction. Rows locked by a concurrent run are skipped (SKIP LOCKED), so
     * two runs split the work instead of waiting for, or repeating, each other.
     */
    @Query(value = """
            SELECT * FROM bookings
            WHERE status = 'HOLD' AND hold_expires_at <= CAST(:now AS timestamptz)
            ORDER BY hold_expires_at, id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Booking> lockExpiredHolds(@Param("now") Instant now, @Param("limit") int limit);
}
