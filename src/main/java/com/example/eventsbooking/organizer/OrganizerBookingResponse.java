package com.example.eventsbooking.organizer;

import com.example.eventsbooking.booking.BookingStatus;

import java.time.Instant;

/**
 * A booking as the organizer of the event sees it. Deliberately minimal personal data: an attendee is an id and the
 * name they chose to show, never an email or a phone number.
 */
public record OrganizerBookingResponse(Long id, BookingStatus status, int seats, Instant createdAt,
        Instant holdExpiresAt, Instant cancelledAt, Attendee attendee) {

    public record Attendee(long id, String name) {
    }
}
