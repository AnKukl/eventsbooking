package com.example.eventsbooking.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "bookings",
        uniqueConstraints = @UniqueConstraint(name = "uq_bookings_event_user", columnNames = {"event_id", "user_id"}))
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

    protected Booking() {
        // for JPA
    }

    public Booking(long eventId, long userId) {
        this.eventId = eventId;
        this.userId = userId;
        this.createdAt = Instant.now();
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
}
