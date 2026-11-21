package com.example.eventsbooking.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record EventResponse(
        Long id,
        String title,
        String description,
        Instant startsAt,
        int capacity,
        int bookedCount,
        long organizerId,
        SeatStatus seatStatus,
        @JsonProperty("isFavorite") boolean isFavorite) {

    public static EventResponse from(Event event, boolean favorite) {
        return new EventResponse(event.getId(), event.getTitle(), event.getDescription(),
                event.getStartsAt(), event.getCapacity(), event.getBookedCount(), event.getOrganizerId(),
                event.seatStatus(), favorite);
    }
}
