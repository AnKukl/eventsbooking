package com.example.eventsbooking.booking;

import java.time.Instant;

public record BookingResponse(Long id, long eventId, long userId, int seats, BookingStatus status,
        Instant holdExpiresAt, String cancellationReason, Instant createdAt, Instant cancelledAt) {

    static BookingResponse from(Booking booking) {
        return new BookingResponse(booking.getId(), booking.getEventId(), booking.getUserId(), booking.getSeats(),
                booking.getStatus(), booking.getHoldExpiresAt(), booking.getCancellationReason(),
                booking.getCreatedAt(), booking.getCancelledAt());
    }
}
