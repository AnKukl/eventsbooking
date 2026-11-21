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
 * A booking of {@code seats} seats. It starts as a HOLD that keeps the seats until {@code holdExpiresAt}; confirming
 * it makes it CONFIRMED, cancelling it frees the seats. One active (HOLD or CONFIRMED) booking per user and event is
 * enforced by the partial unique index {@code uq_bookings_active_event_user}.
 */
@Entity
@Table(name = "bookings")
public class Booking {

    /** Reason stored when the system releases an unconfirmed hold; not a value clients may send. */
    public static final String HOLD_EXPIRED = "HOLD_EXPIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long eventId;

    @Column(nullable = false)
    private long userId;

    @Column(nullable = false)
    private int seats;

    @Column(nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BookingStatus status = BookingStatus.HOLD;

    private Instant holdExpiresAt;

    private Instant cancelledAt;

    @Column(length = 32)
    private String cancellationReason;

    protected Booking() {
        // for JPA
    }

    public Booking(long eventId, long userId, int seats, Instant createdAt, Instant holdExpiresAt) {
        this.eventId = eventId;
        this.userId = userId;
        this.seats = seats;
        this.createdAt = createdAt;
        this.holdExpiresAt = holdExpiresAt;
    }

    /** HOLD to CONFIRMED. The caller checks the hold has not expired. */
    public void confirm(Instant now) {
        if (status != BookingStatus.HOLD) {
            throw new BookingNotOnHoldException(id == null ? -1 : id);
        }
        this.status = BookingStatus.CONFIRMED;
    }

    /**
     * Cancels the booking. With {@code refund} it becomes REFUND_PENDING (a refund was requested), otherwise CANCELLED.
     * The caller releases the seats.
     */
    public void cancel(Instant at, String reason, boolean refund) {
        if (status == BookingStatus.CANCELLED || status == BookingStatus.REFUND_PENDING) {
            throw new BookingAlreadyCancelledException(id == null ? -1 : id);
        }
        this.status = refund ? BookingStatus.REFUND_PENDING : BookingStatus.CANCELLED;
        this.cancelledAt = at;
        this.cancellationReason = reason;
    }

    /** An unconfirmed hold whose time ran out. The caller releases the seats. */
    public void expire(Instant at) {
        cancel(at, HOLD_EXPIRED, false);
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

    public int getSeats() {
        return seats;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public Instant getHoldExpiresAt() {
        return holdExpiresAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }
}
