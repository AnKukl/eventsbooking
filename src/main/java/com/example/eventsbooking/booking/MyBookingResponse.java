package com.example.eventsbooking.booking;

import com.example.eventsbooking.event.Event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A booking in "my bookings", with what the cabinet shows about its event. {@code event} is null if the event no
 * longer exists.
 */
public record MyBookingResponse(Long id, long eventId, long userId, int seats, BookingStatus status, Instant holdExpiresAt,
        String cancellationReason, Instant createdAt, Instant cancelledAt, EventSummary event) {

    public record EventSummary(String title, Instant startsAt, String venue, BigDecimal price, BigDecimal latitude,
            BigDecimal longitude) {
    }

    static MyBookingResponse from(Booking booking, Event event) {
        return new MyBookingResponse(booking.getId(), booking.getEventId(), booking.getUserId(), booking.getSeats(),
                booking.getStatus(), booking.getHoldExpiresAt(), booking.getCancellationReason(), booking.getCreatedAt(),
                booking.getCancelledAt(), event == null ? null : new EventSummary(event.getTitle(), event.getStartsAt(),
                        event.getVenue(), event.getPrice(), event.getLatitude(), event.getLongitude()));
    }
}
