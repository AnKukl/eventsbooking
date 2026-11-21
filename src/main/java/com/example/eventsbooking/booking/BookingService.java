package com.example.eventsbooking.booking;

import com.example.eventsbooking.common.ConflictException;
import com.example.eventsbooking.event.Event;
import com.example.eventsbooking.event.EventNotFoundException;
import com.example.eventsbooking.event.EventRepository;
import com.example.eventsbooking.event.EventStatus;
import com.example.eventsbooking.event.SeatReleased;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Booking, confirmation and cancellation.
 *
 * <h2>Why optimistic locking (@Version on Event)</h2>
 * Booking and cancelling change {@code Event.bookedCount}, so two transactions that read the same state cannot both
 * commit: the loser gets an {@link OptimisticLockingFailureException} and is simply retried (it either takes what is left
 * or sees the event is full and answers 409). No database locks are held between reading and writing, and there are no
 * deadlocks. Second line of defence: a unique index on the active booking per (event, user) and
 * {@code CHECK (booked_count <= capacity)}. The trade-off, and when to use an atomic UPDATE instead, are in
 * docs/decisions.md.
 *
 * <h2>Life of a booking</h2>
 * <ul>
 *   <li>{@link #book}: a HOLD that keeps the seats for {@code app.booking.hold-duration} (10 minutes).</li>
 *   <li>{@link #confirm}: HOLD to CONFIRMED before the hold expires; only now is the booking announced to Kafka.</li>
 *   <li>{@link #cancel}: seats are released. A confirmed booking cancelled at least {@code cancellation-notice} (24 h)
 *       before the start is REFUND_PENDING and a refund is requested; later (or an unpaid HOLD) it is simply CANCELLED.</li>
 *   <li>An unconfirmed hold is released by {@link HoldExpiryService}.</li>
 * </ul>
 *
 * <h2>Time</h2>
 * Current time comes only from {@link Clock}, so every rule is tested without sleeping.
 */
@Service
public class BookingService {

    static final int MAX_ATTEMPTS = 5;

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final EventRepository events;
    private final BookingRepository bookings;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final BookingProperties properties;
    private final BookingEventPublisher publisher;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentGateway payments;
    private final IdempotencyKeys idempotencyKeys;

    public BookingService(EventRepository events, BookingRepository bookings,
            PlatformTransactionManager transactionManager, Clock clock, BookingProperties properties,
            BookingEventPublisher publisher, ApplicationEventPublisher eventPublisher, PaymentGateway payments,
            IdempotencyKeys idempotencyKeys) {
        this.events = events;
        this.bookings = bookings;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.properties = properties;
        this.publisher = publisher;
        this.eventPublisher = eventPublisher;
        this.payments = payments;
        this.idempotencyKeys = idempotencyKeys;
    }

    /** One seat, no Idempotency-Key. */
    public Booking book(long eventId, long userId) {
        return book(eventId, userId, 1, null).booking();
    }

    /**
     * Holds {@code seats} seats for the user. With an {@code idempotencyKey} a repeat of the same request returns the
     * booking made the first time ({@code created == false}) instead of failing as "already booked".
     */
    public BookingOutcome book(long eventId, long userId, int seats, String idempotencyKey) {
        int max = properties.maxSeatsPerBooking();
        if (seats < 1 || seats > max) {
            throw new InvalidSeatCountException(max);
        }
        try {
            return inTransactionWithRetry(eventId, () -> bookOnce(eventId, userId, seats, idempotencyKey));
        } catch (DataIntegrityViolationException e) {
            if (bookings.existsByEventIdAndUserIdAndStatusIn(eventId, userId, BookingStatus.ACTIVE)) {
                throw new AlreadyBookedException(eventId, userId);
            }
            throw e;
        }
    }

    /**
     * HOLD to CONFIRMED (the payment stage will call this when the payment succeeds). Confirming a booking that is
     * already confirmed is harmless. The Kafka message is sent only after the transaction has committed.
     */
    public Booking confirm(long bookingId, long requesterId) {
        Confirmation confirmation = transaction.execute(status -> confirmOnce(bookingId, requesterId));
        if (confirmation.changed()) {
            publisher.publish(confirmation.booking());
        }
        return confirmation.booking();
    }

    /**
     * Cancels the booking and frees its seats. A confirmed booking cancelled at least {@code cancellation-notice}
     * before the start becomes REFUND_PENDING and the payment gateway is asked for a refund; otherwise CANCELLED.
     */
    public Booking cancel(long bookingId, long requesterId, CancellationReason reason) {
        long eventId = ownBooking(bookingId, requesterId).getEventId();
        Booking cancelled = inTransactionWithRetry(eventId, () -> cancelOnce(bookingId, requesterId, reason));
        if (cancelled.getStatus() == BookingStatus.REFUND_PENDING) {
            requestRefund(cancelled);
        }
        return cancelled;
    }

    /** Bookings of one user, newest first (the caller passes the authenticated user). */
    public Page<Booking> findMine(long userId, Pageable pageable) {
        return bookings.findByUserId(userId, pageable);
    }

    /**
     * Not annotated with @Transactional on purpose: every attempt runs in its own new transaction, otherwise a retry
     * after a version conflict would run inside the already rolled-back one and read stale data again.
     */
    private <T> T inTransactionWithRetry(long eventId, Supplier<T> action) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transaction.execute(status -> action.get());
            } catch (OptimisticLockingFailureException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new ConcurrentBookingException(eventId);
                }
            }
        }
    }

    private BookingOutcome bookOnce(long eventId, long userId, int seats, String key) {
        if (key != null && !idempotencyKeys.claim(userId, key, eventId, seats)) {
            return replay(userId, key, eventId, seats);
        }
        Event event = events.findById(eventId)
                .filter(found -> found.getStatus() == EventStatus.PUBLISHED)   // a draft does not exist for bookers
                .orElseThrow(() -> new EventNotFoundException(eventId));
        Instant now = clock.instant();
        if (!event.getStartsAt().isAfter(now)) {
            throw new EventAlreadyStartedException(eventId);
        }
        if (bookings.existsByEventIdAndUserIdAndStatusIn(eventId, userId, BookingStatus.ACTIVE)) {
            throw new AlreadyBookedException(eventId, userId);
        }
        event.book(seats);
        // saveAndFlush: a version conflict or a unique violation shows up here, inside the attempt
        Booking booking = bookings.saveAndFlush(
                new Booking(eventId, userId, seats, now, now.plus(properties.holdDuration())));
        if (key != null) {
            idempotencyKeys.attach(userId, key, booking.getId());
        }
        return new BookingOutcome(booking, true);
    }

    private BookingOutcome replay(long userId, String key, long eventId, int seats) {
        IdempotencyKeys.Entry first = idempotencyKeys.find(userId, key)
                .orElseThrow(() -> new IllegalStateException("Idempotency key is taken but has no booking"));
        if (first.eventId() != eventId || first.seats() != seats) {
            throw new IdempotencyKeyReusedException();
        }
        Booking booking = bookings.findById(first.bookingId())
                .orElseThrow(() -> new BookingNotFoundException(first.bookingId()));
        return new BookingOutcome(booking, false);
    }

    private record Confirmation(Booking booking, boolean changed) {
    }

    private Confirmation confirmOnce(long bookingId, long requesterId) {
        Booking booking = ownBooking(bookingId, requesterId);
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return new Confirmation(booking, false);
        }
        if (booking.getStatus() != BookingStatus.HOLD) {
            throw new BookingNotOnHoldException(bookingId);
        }
        Instant now = clock.instant();
        if (!now.isBefore(booking.getHoldExpiresAt())) {
            throw new HoldExpiredException();
        }
        booking.confirm(now);
        return new Confirmation(bookings.saveAndFlush(booking), true);
    }

    private Booking cancelOnce(long bookingId, long requesterId, CancellationReason reason) {
        Booking booking = ownBooking(bookingId, requesterId);
        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.REFUND_PENDING) {
            throw new BookingAlreadyCancelledException(bookingId);
        }
        Event event = events.findById(booking.getEventId())
                .orElseThrow(() -> new EventNotFoundException(booking.getEventId()));
        Instant now = clock.instant();
        if (!event.getStartsAt().isAfter(now)) {
            throw new ConflictException("Event already started",
                    "The event has already started: its bookings can no longer be cancelled");
        }
        // an unconfirmed hold was never paid for: nothing to refund, whenever it is cancelled
        Duration untilStart = Duration.between(now, event.getStartsAt());
        boolean refund = booking.getStatus() == BookingStatus.CONFIRMED
                && untilStart.compareTo(properties.cancellationNotice()) >= 0;

        booking.cancel(now, reason.name(), refund);
        event.release(booking.getSeats());
        Booking saved = bookings.saveAndFlush(booking);
        // inside this attempt's transaction: the waitlist hears about it only if the attempt commits
        eventPublisher.publishEvent(new SeatReleased(booking.getEventId(), UUID.randomUUID(), booking.getSeats()));
        return saved;
    }

    /** Someone else's booking looks exactly like a missing one: its existence is not revealed. */
    private Booking ownBooking(long bookingId, long requesterId) {
        return bookings.findById(bookingId)
                .filter(found -> found.getUserId() == requesterId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    /**
     * After the commit. A failing gateway must not undo the cancellation: the booking stays REFUND_PENDING and the
     * request is repeated later (the payment stage reconciles pending refunds).
     */
    private void requestRefund(Booking booking) {
        try {
            payments.refund(booking);
        } catch (RuntimeException e) {
            log.error("Refund request for booking {} failed; it stays REFUND_PENDING", booking.getId(), e);
        }
    }
}
