package com.example.eventsbooking.organizer;

import com.example.eventsbooking.common.ConflictException;

/** The organizer edited an older copy of the event: somebody (or another tab) saved a newer one in the meantime. */
public class StaleEventVersionException extends ConflictException {

    public StaleEventVersionException(long eventId) {
        super("Event was changed", "Event " + eventId + " was changed since you loaded it: reload it and apply your edit again");
    }
}
