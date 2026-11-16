package com.example.eventsbooking.booking;

import com.example.eventsbooking.common.ConflictException;

public class AlreadyBookedException extends ConflictException {

    public AlreadyBookedException(long eventId, long userId) {
        super("Already booked", "User " + userId + " has already booked event " + eventId);
    }
}
