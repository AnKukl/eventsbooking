package com.example.eventsbooking.booking;

import java.time.Instant;
import java.util.UUID;

/**
 * Message published to {@code booking-events}.
 *
 * @param eventId       unique id of this message; consumers use it as the idempotency key
 * @param bookingId     id of the new booking
 * @param targetEventId id of the event (the show) that was booked
 * @param userId        who booked
 * @param occurredAt    when the booking was published
 */
public record BookingCreated(UUID eventId, long bookingId, long targetEventId, long userId, Instant occurredAt) {

    public static final String TOPIC = "booking-events";
}
