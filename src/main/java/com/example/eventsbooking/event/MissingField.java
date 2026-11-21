package com.example.eventsbooking.event;

/** A field that must be filled in before an event can be published. */
public record MissingField(String field, String message) {
}
