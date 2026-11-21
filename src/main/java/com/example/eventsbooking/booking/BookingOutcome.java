package com.example.eventsbooking.booking;

/** @param created false when the request was a repeat of an earlier one (same Idempotency-Key) */
public record BookingOutcome(Booking booking, boolean created) {
}
