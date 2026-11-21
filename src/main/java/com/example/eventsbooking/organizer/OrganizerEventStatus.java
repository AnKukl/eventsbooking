package com.example.eventsbooking.organizer;

import com.example.eventsbooking.event.Event;
import com.example.eventsbooking.event.EventStatus;
import com.example.eventsbooking.event.SeatStatus;

/** The status an organizer sees: the stored one, with "sold out" derived from the seat counters. */
public enum OrganizerEventStatus {
    DRAFT,
    PUBLISHED,
    SOLD_OUT,
    CANCELLED;

    static OrganizerEventStatus of(Event event) {
        if (event.getStatus() == EventStatus.DRAFT) {
            return DRAFT;
        }
        if (event.getStatus() == EventStatus.CANCELLED) {
            return CANCELLED;
        }
        return event.seatStatus() == SeatStatus.SOLD_OUT ? SOLD_OUT : PUBLISHED;
    }
}
