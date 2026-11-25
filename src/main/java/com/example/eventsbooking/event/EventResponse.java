package com.example.eventsbooking.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record EventResponse(
        Long id,
        String title,
        String description,
        String shortDescription,
        Instant startsAt,
        int capacity,
        int bookedCount,
        long organizerId,
        SeatStatus seatStatus,
        @JsonProperty("isFavorite") boolean isFavorite,
        String venue,
        java.math.BigDecimal price,
        /* the first picture of the event (its cover), or null */
        String coverUrl) {

    public static EventResponse from(Event event, boolean favorite, String coverUrl) {
        return new EventResponse(event.getId(), event.getTitle(), event.getDescription(),
                event.getShortDescription(), event.getStartsAt(), event.getCapacity(), event.getBookedCount(), event.getOrganizerId(),
                event.seatStatus(), favorite, event.getVenue(), event.getPrice(), coverUrl);
    }
}
