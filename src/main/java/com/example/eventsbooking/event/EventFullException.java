package com.example.eventsbooking.event;

import com.example.eventsbooking.common.ConflictException;

public class EventFullException extends ConflictException {

    public EventFullException(Long eventId) {
        super("Event is full", "Event with id " + eventId + " has no free seats left");
    }
}
