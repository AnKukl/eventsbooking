package com.example.eventsbooking.notification;

/** The message cannot be parsed; retrying will never help, so the error handler does not retry it. */
class InvalidBookingEventException extends RuntimeException {

    InvalidBookingEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
