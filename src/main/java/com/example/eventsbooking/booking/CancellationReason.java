package com.example.eventsbooking.booking;

/** Why the user cancelled. The system's own reason (an expired hold) is deliberately not selectable by clients. */
public enum CancellationReason {
    CHANGED_PLANS,
    SCHEDULE_CONFLICT,
    FOUND_ALTERNATIVE,
    TOO_EXPENSIVE,
    OTHER
}
