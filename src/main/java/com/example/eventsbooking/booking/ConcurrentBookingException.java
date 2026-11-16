package com.example.eventsbooking.booking;

import com.example.eventsbooking.common.ConflictException;

public class ConcurrentBookingException extends ConflictException {

    public ConcurrentBookingException(long eventId) {
        super("Concurrent booking conflict",
                "Event " + eventId + " is being booked concurrently, please retry");
    }
}
