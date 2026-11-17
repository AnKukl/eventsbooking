package com.example.eventsbooking.booking;

import com.example.eventsbooking.common.ConflictException;

import java.time.Duration;

public class CancellationTooLateException extends ConflictException {

    public CancellationTooLateException(Duration notice) {
        super("Cancellation not allowed",
                "A booking cannot be cancelled less than " + describe(notice) + " before the event starts");
    }

    private static String describe(Duration notice) {
        return notice.toMinutes() % 60 == 0 ? notice.toHours() + " hours" : notice.toMinutes() + " minutes";
    }
}
