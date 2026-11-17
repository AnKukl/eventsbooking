package com.example.eventsbooking.booking;

import com.example.eventsbooking.common.ConflictException;

public class BookingAlreadyCancelledException extends ConflictException {

    public BookingAlreadyCancelledException(long bookingId) {
        super("Already cancelled", "Booking " + bookingId + " has already been cancelled");
    }
}
