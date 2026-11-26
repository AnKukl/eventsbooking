package com.example.eventsbooking.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Idempotent insert of a booking confirmation: the unique constraint on {@code event_id} decides atomically, so two
     * consumers (or a redelivery) racing on the same message cannot both insert. Returns 1 if inserted, 0 if it was a
     * duplicate.
     */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO notifications (event_id, booking_id, target_event_id, user_id, type, title, body, created_at)
            VALUES (:key, :bookingId, :targetEventId, :userId, 'BOOKING_CONFIRMED', :title, :body, :createdAt)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertBookingConfirmed(@Param("key") UUID key, @Param("bookingId") long bookingId,
            @Param("targetEventId") long targetEventId, @Param("userId") long userId, @Param("title") String title,
            @Param("body") String body, @Param("createdAt") Instant createdAt);

    /** Idempotent insert of "a seat is free" for one user; 1 if inserted, 0 if {@code key} was already used. */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO notifications (event_id, target_event_id, user_id, type, title, body, created_at)
            VALUES (:key, :targetEventId, :userId, 'SEAT_AVAILABLE', :title, :body, :createdAt)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertSeatAvailable(@Param("key") UUID key, @Param("targetEventId") long targetEventId,
            @Param("userId") long userId, @Param("title") String title, @Param("body") String body,
            @Param("createdAt") Instant createdAt);

    /** The caller's feed, newest first. The order is part of the query: pass an unsorted Pageable. */
    Page<Notification> findByUserIdOrderByCreatedAtDescIdDesc(long userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(long userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "UPDATE Notification n SET n.readAt = :now WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllRead(@org.springframework.data.repository.query.Param("userId") long userId,
            @org.springframework.data.repository.query.Param("now") java.time.Instant now);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "UPDATE Notification n SET n.readAt = :now WHERE n.userId = :userId AND n.readAt IS NULL AND n.id IN :ids")
    int markRead(@org.springframework.data.repository.query.Param("userId") long userId,
            @org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids,
            @org.springframework.data.repository.query.Param("now") java.time.Instant now);
}
