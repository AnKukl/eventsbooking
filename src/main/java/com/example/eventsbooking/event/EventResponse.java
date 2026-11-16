package com.example.eventsbooking.event;

import java.time.Instant;

public record EventResponse(
        Long id,
        String title,
        String description,
        Instant startsAt,
        int capacity,
        int bookedCount,
        long organizerId) {

    static EventResponse from(Event event) {
        return new EventResponse(event.getId(), event.getTitle(), event.getDescription(),
                event.getStartsAt(), event.getCapacity(), event.getBookedCount(), event.getOrganizerId());
    }
}
