package com.example.eventsbooking.organizer;

import com.example.eventsbooking.event.Event;
import com.example.eventsbooking.event.EventNotFoundException;
import com.example.eventsbooking.event.EventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class OrganizerBookingsService {

    private final EventRepository events;
    private final OrganizerBookingRepository bookings;

    OrganizerBookingsService(EventRepository events, OrganizerBookingRepository bookings) {
        this.events = events;
        this.bookings = bookings;
    }

    /** The bookings of one of the caller's events, newest first, one page at a time. */
    public Page<OrganizerBookingResponse> list(long eventId, long organizerId, Pageable pageable) {
        Event event = events.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
        if (event.getOrganizerId() != organizerId) {
            throw new AccessDeniedException("Event " + eventId + " belongs to another organizer");
        }
        List<OrganizerBookingResponse> content = bookings.findByEvent(eventId, pageable.getPageSize(), pageable.getOffset());
        return new PageImpl<>(content, pageable, bookings.countByEvent(eventId));
    }
}
