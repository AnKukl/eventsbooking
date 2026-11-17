package com.example.eventsbooking.booking;

import com.example.eventsbooking.common.ConflictException;

public class EventAlreadyStartedException extends ConflictException {

    public EventAlreadyStartedException(long eventId) {
        super("Event already started", "Event " + eventId + " has already started and cannot be booked");
    }
}
