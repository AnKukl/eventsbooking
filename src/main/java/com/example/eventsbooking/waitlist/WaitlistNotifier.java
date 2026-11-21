package com.example.eventsbooking.waitlist;

import com.example.eventsbooking.event.Event;
import com.example.eventsbooking.event.EventRepository;
import com.example.eventsbooking.event.SeatReleased;
import com.example.eventsbooking.notification.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Tells the head of the queue that a seat is free: one person per freed seat, never everybody at once.
 *
 * <p>Runs in its own transaction: it is called after the releasing transaction has committed, when that transaction can
 * no longer take part in new writes.
 */
@Service
public class WaitlistNotifier {

    private final WaitlistRepository waitlist;
    private final NotificationRepository notifications;
    private final EventRepository events;
    private final Clock clock;

    public WaitlistNotifier(WaitlistRepository waitlist, NotificationRepository notifications, EventRepository events,
            Clock clock) {
        this.waitlist = waitlist;
        this.notifications = notifications;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Notifies up to {@code released.seats()} people. Idempotent per release: the notification is stored under a key
     * derived from the release, and a taken key means this release was already handled, so nobody (not even the next
     * person in line) is notified a second time.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyNext(SeatReleased released) {
        Optional<Event> event = events.findById(released.eventId());
        if (event.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        for (int seat = 0; seat < released.seats(); seat++) {
            Optional<WaitlistEntry> next = waitlist.findNextToNotify(released.eventId());
            if (next.isEmpty()) {
                return;
            }
            WaitlistEntry entry = next.get();
            Event target = event.get();
            int inserted = notifications.insertSeatAvailable(keyFor(released.releaseId(), seat), target.getId(),
                    entry.getUserId(), "A seat is available",
                    "A seat has opened up for \"" + target.getTitle() + "\". Book it before someone else does.", now);
            if (inserted == 0) {
                return;   // this release was already handled
            }
            waitlist.markNotified(entry.getId(), now);
        }
    }

    /** Key of the seat-th notification of a release: the release id itself for the first, derived for the rest. */
    static UUID keyFor(UUID releaseId, int seat) {
        return seat == 0 ? releaseId
                : UUID.nameUUIDFromBytes((releaseId + "#" + seat).getBytes(StandardCharsets.UTF_8));
    }
}
