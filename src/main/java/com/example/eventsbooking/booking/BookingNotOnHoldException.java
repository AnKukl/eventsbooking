package com.example.eventsbooking.booking;

import com.example.eventsbooking.common.ConflictException;

public class BookingNotOnHoldException extends ConflictException {

    public BookingNotOnHoldException(long bookingId) {
        super("Booking is not on hold", "Booking " + bookingId + " is not on hold, so it cannot be confirmed");
    }
}
