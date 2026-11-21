package com.example.eventsbooking.event;

/** Stored state of an event. "Sold out" is not stored: it follows from the seat counters. */
public enum EventStatus {
    /** Only the organizer sees it, in the cabinet. */
    DRAFT,
    PUBLISHED,
    /** Reserved: no endpoint cancels an event yet. */
    CANCELLED
}
