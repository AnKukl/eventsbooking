package com.example.eventsbooking.organizer;

import com.example.eventsbooking.event.MissingField;

import java.util.List;

/** Publishing refused: these fields are still empty or invalid. Reported as 422 with the list. */
public class EventNotPublishableException extends RuntimeException {

    private final List<MissingField> missing;

    public EventNotPublishableException(List<MissingField> missing) {
        super("The event cannot be published yet: " + missing.size() + " field(s) need attention");
        this.missing = List.copyOf(missing);
    }

    public List<MissingField> getMissing() {
        return missing;
    }
}
