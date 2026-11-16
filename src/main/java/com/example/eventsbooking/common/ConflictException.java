package com.example.eventsbooking.common;

/** Base class for business conflicts that are reported to the client as HTTP 409. */
public class ConflictException extends RuntimeException {

    private final String title;

    public ConflictException(String title, String detail) {
        super(detail);
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
