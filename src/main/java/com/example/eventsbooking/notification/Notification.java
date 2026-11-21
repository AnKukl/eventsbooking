package com.example.eventsbooking.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * An item of a user's feed. Rows are created by the insert methods of {@link NotificationRepository}, which are
 * idempotent on {@code eventId}; the entity is only used for reading.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Idempotency key of the source (a BookingCreated message or a seat release). */
    @Column(nullable = false)
    private UUID eventId;

    private Long bookingId;

    private Long targetEventId;

    @Column(nullable = false)
    private long userId;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String body;

    private Instant readAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Long getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public Long getTargetEventId() {
        return targetEventId;
    }

    public long getUserId() {
        return userId;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
