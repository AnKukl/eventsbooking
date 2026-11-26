package com.example.eventsbooking.organizer;

import com.example.eventsbooking.event.Event;
import com.example.eventsbooking.event.MediaResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** An event as its organizer sees it. {@code version} is what the next PATCH must send back. */
public record OrganizerEventResponse(
        Long id,
        String title,
        String description,
        String shortDescription,
        Instant startsAt,
        int capacity,
        int bookedCount,
        String venue,
        BigDecimal price,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean waitlistEnabled,
        OrganizerEventStatus status,
        long version,
        List<String> audienceFit,
        List<String> audienceNotFit,
        List<MediaResponse> media) {

    /** Reads the lazy lists: call it inside the transaction. */
    static OrganizerEventResponse from(Event event) {
        return new OrganizerEventResponse(event.getId(), event.getTitle(), event.getDescription(),
                event.getShortDescription(), event.getStartsAt(), event.getCapacity(), event.getBookedCount(),
                event.getVenue(), event.getPrice(), event.getLatitude(), event.getLongitude(), event.isWaitlistEnabled(), OrganizerEventStatus.of(event),
                event.getRevision(), event.getAudienceFit(), event.getAudienceNotFit(),
                event.getMedia().stream().map(media -> new MediaResponse(media.url(), media.alt())).toList());
    }
}
