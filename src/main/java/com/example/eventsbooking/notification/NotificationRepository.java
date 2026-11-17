package com.example.eventsbooking.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Idempotent insert: the unique constraint on {@code event_id} decides atomically, so two consumers (or a
     * redelivery) racing on the same message cannot both insert. Returns 1 if inserted, 0 if it was a duplicate.
     */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO notifications (event_id, booking_id, user_id, message)
            VALUES (:eventId, :bookingId, :userId, :message)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") UUID eventId, @Param("bookingId") long bookingId,
            @Param("userId") long userId, @Param("message") String message);
}
