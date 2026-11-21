package com.example.eventsbooking.event;

/**
 * Availability of seats as shown to clients. {@code WAITLIST} is part of the contract for the frontend but is not
 * produced yet: there is no waiting list, so a full event is {@code SOLD_OUT}.
 */
public enum SeatStatus {
    AVAILABLE,
    SOLD_OUT,
    WAITLIST
}
