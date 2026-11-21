package com.example.eventsbooking.booking;

/** The requested number of seats is outside what one booking may contain; reported as 400. */
public class InvalidSeatCountException extends RuntimeException {

    public InvalidSeatCountException(int max) {
        super("Seats must be between 1 and " + max);
    }
}
