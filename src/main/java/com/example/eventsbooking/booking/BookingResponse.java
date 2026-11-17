package com.example.eventsbooking.booking;

import java.time.Instant;

public record BookingResponse(Long id, long eventId, long userId, BookingStatus status,
        Instant createdAt, Instant cancelledAt) {

    static BookingResponse from(Booking booking) {
        return new BookingResponse(booking.getId(), booking.getEventId(), booking.getUserId(),
                booking.getStatus(), booking.getCreatedAt(), booking.getCancelledAt());
    }
}
