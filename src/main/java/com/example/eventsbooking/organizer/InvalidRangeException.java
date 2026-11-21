package com.example.eventsbooking.organizer;

public class InvalidRangeException extends RuntimeException {

    public InvalidRangeException() {
        super("range must be a number of days from 1 to 365 followed by d, for example 7d");
    }
}
