package com.example.eventsbooking.waitlist;

import com.example.eventsbooking.booking.BookingRepository;
import com.example.eventsbooking.booking.BookingStatus;
import com.example.eventsbooking.common.ConflictException;
import com.example.eventsbooking.event.Event;
import com.example.eventsbooking.event.EventNotFoundException;
import com.example.eventsbooking.event.EventRepository;
import com.example.eventsbooking.event.SeatStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@Transactional(readOnly = true)
public class WaitlistService {

    private final WaitlistRepository waitlist;
    private final EventRepository events;
    private final BookingRepository bookings;
    private final Clock clock;

    public WaitlistService(WaitlistRepository waitlist, EventRepository events, BookingRepository bookings,
            Clock clock) {
        this.waitlist = waitlist;
        this.events = events;
        this.bookings = bookings;
        this.clock = clock;
    }

    /**
     * Puts the user at the end of the queue of a sold-out event. Idempotent: returns true if the entry was created,
     * false if the user was already waiting (the original channel is kept).
     */
    @Transactional
    public boolean join(long userId, long eventId, WaitlistChannel channel) {
        Event event = events.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
        if (waitlist.existsByUserIdAndEventId(userId, eventId)) {
            return false;
        }
        if (!event.getStartsAt().isAfter(clock.instant())) {
            throw new ConflictException("Event already started", "Event " + eventId + " has already started");
        }
        if (event.seatStatus() != SeatStatus.SOLD_OUT) {
            throw new ConflictException("Event has free seats",
                    "Event " + eventId + " has free seats: book it instead of waiting");
        }
        if (bookings.existsByEventIdAndUserIdAndStatusIn(eventId, userId, BookingStatus.ACTIVE)) {
            throw new ConflictException("Already booked", "You have already booked event " + eventId);
        }
        // the unique constraint decides a race between two identical requests
        return waitlist.insertIfAbsent(userId, eventId, channel.name(), clock.instant()) == 1;
    }
}
