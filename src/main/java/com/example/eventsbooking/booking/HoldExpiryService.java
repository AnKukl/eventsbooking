package com.example.eventsbooking.booking;

import com.example.eventsbooking.event.EventRepository;
import com.example.eventsbooking.event.SeatReleased;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Releases holds nobody confirmed in time and gives their seats back to the event.
 *
 * <p>One transaction per run, over holds locked with {@code FOR UPDATE SKIP LOCKED}: two runs (or two instances) split
 * the expired holds between them instead of releasing a seat twice. The seat counter is changed through the event's
 * {@code @Version}, like every other seat change: if a booking or another run changed the event in the meantime, this run
 * rolls back as a whole and the next tick (30 seconds later) repeats it.
 */
@Service
public class HoldExpiryService {

    static final int BATCH_SIZE = 100;

    private final BookingRepository bookings;
    private final EventRepository events;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public HoldExpiryService(BookingRepository bookings, EventRepository events,
            ApplicationEventPublisher eventPublisher, Clock clock) {
        this.bookings = bookings;
        this.events = events;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** Returns how many holds were released. */
    @Transactional
    public int expireHolds() {
        Instant now = clock.instant();
        List<Booking> expired = bookings.lockExpiredHolds(now, BATCH_SIZE);
        if (expired.isEmpty()) {
            return 0;
        }
        Map<Long, Integer> seatsByEvent = new LinkedHashMap<>();
        for (Booking booking : expired) {
            booking.expire(now);
            seatsByEvent.merge(booking.getEventId(), booking.getSeats(), Integer::sum);
        }
        seatsByEvent.forEach((eventId, seats) -> {
            events.findById(eventId).ifPresent(event -> event.release(seats));
            // delivered to the waitlist only if this transaction commits
            eventPublisher.publishEvent(new SeatReleased(eventId, UUID.randomUUID(), seats));
        });
        return expired.size();
    }
}
