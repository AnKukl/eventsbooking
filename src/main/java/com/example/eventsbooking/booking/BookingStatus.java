package com.example.eventsbooking.booking;

import java.util.EnumSet;
import java.util.Set;

public enum BookingStatus {
    /** Seats are held for a few minutes, waiting for confirmation (payment). */
    HOLD,
    CONFIRMED,
    CANCELLED,
    /** Cancelled in time for a refund that has been requested from the payment gateway. */
    REFUND_PENDING;

    /** The statuses in which a booking keeps its seats and counts against "one booking per event". */
    public static final Set<BookingStatus> ACTIVE = EnumSet.of(HOLD, CONFIRMED);
}
