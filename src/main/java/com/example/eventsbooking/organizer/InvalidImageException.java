package com.example.eventsbooking.organizer;

/** The upload itself is unusable (no files, an empty file); reported as 400. */
public class InvalidImageException extends RuntimeException {

    public InvalidImageException(String message) {
        super(message);
    }
}
