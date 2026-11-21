package com.example.eventsbooking.waitlist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Rows are written by {@link WaitlistRepository#insertIfAbsent}; uniqueness of (user, event) is a database constraint. */
@Entity
@Table(name = "waitlist")
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long userId;

    @Column(nullable = false)
    private long eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private WaitlistChannel channel;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant notifiedAt;

    protected WaitlistEntry() {
        // for JPA
    }

    public Long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public long getEventId() {
        return eventId;
    }

    public WaitlistChannel getChannel() {
        return channel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getNotifiedAt() {
        return notifiedAt;
    }
}
