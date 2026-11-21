package com.example.eventsbooking.booking;

import com.example.eventsbooking.event.Event;
import com.example.eventsbooking.event.EventFullException;
import com.example.eventsbooking.event.EventNotFoundException;
import com.example.eventsbooking.event.EventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Booking (HOLD), confirmation and the idempotency key, with the clock fixed: no sleeping, no real "now". */
class BookingServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private static final Instant STARTS = NOW.plus(Duration.ofDays(10));

    private final EventRepository events = mock(EventRepository.class);
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final BookingEventPublisher kafka = mock(BookingEventPublisher.class);
    private final ApplicationEventPublisher spring = mock(ApplicationEventPublisher.class);
    private final PaymentGateway payments = mock(PaymentGateway.class);
    private final IdempotencyKeys keys = mock(IdempotencyKeys.class);
    private final BookingService service = new BookingService(events, bookings,
            mock(PlatformTransactionManager.class), Clock.fixed(NOW, ZoneOffset.UTC),
            new BookingProperties(Duration.ofHours(24), 4, Duration.ofMinutes(10)), kafka, spring, payments, keys);

    // ---------- booking holds the seats ----------

    @Test
    void aNewBookingIsAHoldOfTenMinutesThatTakesTheSeats() {
        Event event = event(10);

        Booking booking = service.book(1L, 10L, 2, null).booking();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.HOLD);
        assertThat(booking.getSeats()).isEqualTo(2);
        assertThat(booking.getHoldExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(booking.getCreatedAt()).isEqualTo(NOW);
        assertThat(event.getBookedCount()).isEqualTo(2);
        verifyNoInteractions(kafka);   // nobody hears about a booking that is not confirmed yet
    }

    @Test
    void theConvenienceMethodBooksOneSeat() {
        Event event = event(10);

        assertThat(service.book(1L, 10L).getSeats()).isEqualTo(1);
        assertThat(event.getBookedCount()).isEqualTo(1);
    }

    @Test
    void moreSeatsThanTheServerLimitAreRejectedBeforeAnythingIsRead() {
        event(10);

        assertThatThrownBy(() -> service.book(1L, 10L, 5, null)).isInstanceOf(InvalidSeatCountException.class)
                .hasMessageContaining("between 1 and 4");

        verifyNoInteractions(events);
        verifyNoInteractions(bookings);
    }

    @Test
    void exactlyTheLimitIsAllowed() {
        event(10);

        assertThat(service.book(1L, 10L, 4, null).booking().getSeats()).isEqualTo(4);
    }

    @Test
    void zeroOrNegativeSeatsAreRejected() {
        assertThatThrownBy(() -> service.book(1L, 10L, 0, null)).isInstanceOf(InvalidSeatCountException.class);
        assertThatThrownBy(() -> service.book(1L, 10L, -1, null)).isInstanceOf(InvalidSeatCountException.class);
    }

    @Test
    void notEnoughFreeSeatsIsAConflict() {
        Event event = event(3);
        event.book(2);

        assertThatThrownBy(() -> service.book(1L, 10L, 2, null)).isInstanceOf(EventFullException.class);

        assertThat(event.getBookedCount()).isEqualTo(2);
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void theLastFreeSeatsCanBeTakenTogether() {
        Event event = event(3);
        event.book(1);

        service.book(1L, 10L, 2, null);

        assertThat(event.getBookedCount()).isEqualTo(3);
    }

    @Test
    void aSecondActiveBookingOfTheSameEventIsRefused() {
        event(10);
        when(bookings.existsByEventIdAndUserIdAndStatusIn(1L, 10L, BookingStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> service.book(1L, 10L, 1, null)).isInstanceOf(AlreadyBookedException.class);
    }

    @Test
    void aDraftEventCannotBeBookedAndLooksMissing() {
        Event draft = new Event("Draft", null, STARTS, 10, 1L);
        ReflectionTestUtils.setField(draft, "id", 1L);
        ReflectionTestUtils.setField(draft, "status", com.example.eventsbooking.event.EventStatus.DRAFT);
        when(events.findById(1L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.book(1L, 10L, 1, null)).isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void aStartedEventCannotBeBooked() {
        Event event = new Event("Started", null, NOW, 10, 1L);
        ReflectionTestUtils.setField(event, "id", 1L);
        when(events.findById(1L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.book(1L, 10L, 1, null)).isInstanceOf(EventAlreadyStartedException.class);
    }

    @Test
    void anUnknownEventIsNotFound() {
        when(events.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.book(99L, 10L, 1, null)).isInstanceOf(EventNotFoundException.class);
    }

    // ---------- Idempotency-Key ----------

    @Test
    void aFirstRequestWithAKeyBooksAndRemembersTheBooking() {
        event(10);
        when(keys.claim(10L, "k-1", 1L, 2)).thenReturn(true);

        BookingOutcome outcome = service.book(1L, 10L, 2, "k-1");

        assertThat(outcome.created()).isTrue();
        verify(keys).attach(10L, "k-1", outcome.booking().getId());
    }

    @Test
    void aRepeatedKeyReturnsTheSameBookingAndTouchesNothing() {
        Booking existing = holdOf(77L, 2);
        when(keys.claim(10L, "k-1", 1L, 2)).thenReturn(false);
        when(keys.find(10L, "k-1")).thenReturn(Optional.of(new IdempotencyKeys.Entry(1L, 2, 77L)));
        when(bookings.findById(77L)).thenReturn(Optional.of(existing));

        BookingOutcome outcome = service.book(1L, 10L, 2, "k-1");

        assertThat(outcome.created()).isFalse();
        assertThat(outcome.booking()).isSameAs(existing);
        verifyNoInteractions(events);
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void theSameKeyWithAnotherRequestIsAConflict() {
        when(keys.claim(10L, "k-1", 1L, 3)).thenReturn(false);
        when(keys.find(10L, "k-1")).thenReturn(Optional.of(new IdempotencyKeys.Entry(1L, 2, 77L)));

        assertThatThrownBy(() -> service.book(1L, 10L, 3, "k-1")).isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void withoutAKeyTheKeyStoreIsNeverUsed() {
        event(10);

        service.book(1L, 10L, 1, null);

        verifyNoInteractions(keys);
    }

    // ---------- confirm ----------

    @Test
    void confirmingAHoldMakesItConfirmedAndAnnouncesItAfterTheCommit() {
        Booking hold = holdOf(5L, 1);
        when(bookings.saveAndFlush(any(Booking.class))).thenAnswer(returnsFirstArg());

        Booking confirmed = service.confirm(5L, 10L);

        assertThat(confirmed.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(confirmed).isSameAs(hold);
        verify(kafka).publish(hold);
    }

    @Test
    void aHoldCannotBeConfirmedFromTheMomentItExpires() {
        holdOf(5L, 1, NOW);   // expires exactly now

        assertThatThrownBy(() -> service.confirm(5L, 10L)).isInstanceOf(HoldExpiredException.class);

        verifyNoInteractions(kafka);
    }

    @Test
    void aHoldThatHasAnotherNanosecondLeftCanBeConfirmed() {
        holdOf(5L, 1, NOW.plusNanos(1));
        when(bookings.saveAndFlush(any(Booking.class))).thenAnswer(returnsFirstArg());

        assertThat(service.confirm(5L, 10L).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void confirmingAnAlreadyConfirmedBookingIsHarmlessAndAnnouncesNothingAgain() {
        Booking booking = holdOf(5L, 1);
        booking.confirm(NOW);

        assertThat(service.confirm(5L, 10L).getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        verifyNoInteractions(kafka);
    }

    @Test
    void aCancelledBookingCannotBeConfirmed() {
        Booking booking = holdOf(5L, 1);
        booking.cancel(NOW, "OTHER", false);

        assertThatThrownBy(() -> service.confirm(5L, 10L)).isInstanceOf(BookingNotOnHoldException.class);
    }

    @Test
    void someoneElsesBookingLooksLikeAMissingOne() {
        holdOf(5L, 1);

        assertThatThrownBy(() -> service.confirm(5L, 99L)).isInstanceOf(BookingNotFoundException.class);
    }

    // ---------- helpers ----------

    private Event event(int capacity) {
        Event event = new Event("Jazz night", null, STARTS, capacity, 1L);
        ReflectionTestUtils.setField(event, "id", 1L);
        when(events.findById(1L)).thenReturn(Optional.of(event));
        when(bookings.saveAndFlush(any(Booking.class))).thenAnswer(invocation -> {
            Booking saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", 500L);
            }
            return saved;
        });
        return event;
    }

    private Booking holdOf(long id, int seats) {
        return holdOf(id, seats, NOW.plus(Duration.ofMinutes(9)));
    }

    /** A HOLD of user 10 on event 1 with the given id and expiry, registered in the mock repository. */
    private Booking holdOf(long id, int seats, Instant expiresAt) {
        Event event = new Event("Jazz night", null, STARTS, 10, 1L);
        ReflectionTestUtils.setField(event, "id", 1L);
        event.book(seats);
        when(events.findById(1L)).thenReturn(Optional.of(event));
        Booking booking = new Booking(1L, 10L, seats, NOW.minusSeconds(60), expiresAt);
        ReflectionTestUtils.setField(booking, "id", id);
        when(bookings.findById(id)).thenReturn(Optional.of(booking));
        return booking;
    }
}
