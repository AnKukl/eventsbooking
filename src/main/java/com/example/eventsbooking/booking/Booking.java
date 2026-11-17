package com.example.eventsbooking.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Uniqueness (one ACTIVE booking per event and user) is enforced by the partial unique index
 * {@code uq_bookings_active_event_user} created in the migrations.
 */
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long eventId;

    @Column(nullable = false)
    private long userId;

    @Column(nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BookingStatus status = BookingStatus.ACTIVE;

    private Instant cancelledAt;

    protected Booking() {
        // for JPA
    }

    public Booking(long eventId, long userId) {
        this.eventId = eventId;
        this.userId = userId;
        this.createdAt = Instant.now();
    }

    public void cancel(Instant at) {
        if (status == BookingStatus.CANCELLED) {
            throw new BookingAlreadyCancelledException(id == null ? -1 : id);
        }
        this.status = BookingStatus.CANCELLED;
        this.cancelledAt = at;
    }

    public Long getId() {
        return id;
    }

    public long getEventId() {
        return eventId;
    }

    public long getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }
}
