package com.example.eventsbooking.booking;

import com.example.eventsbooking.event.Event;
import com.example.eventsbooking.event.EventNotFoundException;
import com.example.eventsbooking.event.EventRepository;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Time is controlled only through a fixed {@link Clock}: no sleeping, no real "now". */
class BookingCancellationTest {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final EventRepository events = mock(EventRepository.class);
    private final BookingRepository bookings = mock(BookingRepository.class);

    // ---------- 24-hour boundary ----------

    @Test
    void cancellationIsAllowedExactly24HoursBeforeStart() {
        Booking booking = activeBooking(eventStartingAt(NOW.plus(Duration.ofHours(24))));

        Booking cancelled = service(Duration.ofHours(24)).cancel(1L, 100L, 10L);

        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancellationIsRejected23Hours59MinutesBeforeStart() {
        Event event = eventStartingAt(NOW.plus(Duration.ofHours(23).plusMinutes(59)));
        Booking booking = activeBooking(event);

        assertThatThrownBy(() -> service(Duration.ofHours(24)).cancel(1L, 100L, 10L))
                .isInstanceOf(CancellationTooLateException.class);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.ACTIVE);
        assertThat(event.getBookedCount()).isEqualTo(1);
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void cancellationIsRejectedOneNanosecondShortOf24Hours() {
        activeBooking(eventStartingAt(NOW.plus(Duration.ofHours(24)).minusNanos(1)));

        assertThatThrownBy(() -> service(Duration.ofHours(24)).cancel(1L, 100L, 10L))
                .isInstanceOf(CancellationTooLateException.class);
    }

    @Test
    void cancellationIsAllowed24Hours1MinuteBeforeStart() {
        activeBooking(eventStartingAt(NOW.plus(Duration.ofHours(24).plusMinutes(1))));

        assertThat(service(Duration.ofHours(24)).cancel(1L, 100L, 10L).getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancellationIsRejectedAfterTheEventHasStarted() {
        activeBooking(eventStartingAt(NOW.minus(Duration.ofHours(1))));

        assertThatThrownBy(() -> service(Duration.ofHours(24)).cancel(1L, 100L, 10L))
                .isInstanceOf(CancellationTooLateException.class);
    }

    @Test
    void noticePeriodIsConfigurable() {
        activeBooking(eventStartingAt(NOW.plus(Duration.ofHours(47).plusMinutes(59))));
        assertThatThrownBy(() -> service(Duration.ofHours(48)).cancel(1L, 100L, 10L))
                .isInstanceOf(CancellationTooLateException.class);

        activeBooking(eventStartingAt(NOW.plus(Duration.ofHours(48))));
        assertThat(service(Duration.ofHours(48)).cancel(1L, 100L, 10L).getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
    }

    // ---------- effect of cancellation ----------

    @Test
    void cancellationMarksBookingCancelledAndReturnsTheSeat() {
        Event event = eventStartingAt(NOW.plus(Duration.ofDays(10)));
        Booking booking = activeBooking(event);
        assertThat(event.getBookedCount()).isEqualTo(1);

        service(Duration.ofHours(24)).cancel(1L, 100L, 10L);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancelledAt()).isEqualTo(NOW);
        assertThat(event.getBookedCount()).isZero();
        verify(bookings).saveAndFlush(booking);
    }

    @Test
    void cancellingAnAlreadyCancelledBookingIsRejectedAndSeatIsNotReturnedTwice() {
        Event event = eventStartingAt(NOW.plus(Duration.ofDays(10)));
        Booking booking = activeBooking(event);
        booking.cancel(NOW.minusSeconds(60));
        int bookedBefore = event.getBookedCount();

        assertThatThrownBy(() -> service(Duration.ofHours(24)).cancel(1L, 100L, 10L))
                .isInstanceOf(BookingAlreadyCancelledException.class);
        assertThat(event.getBookedCount()).isEqualTo(bookedBefore);
    }

    // ---------- lookup failures ----------

    @Test
    void cancellationOfUnknownBookingFails() {
        eventStartingAt(NOW.plus(Duration.ofDays(10)));
        when(bookings.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(Duration.ofHours(24)).cancel(1L, 100L, 10L))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void cancellationThroughAnotherEventFails() {
        eventStartingAt(NOW.plus(Duration.ofDays(10)));
        when(bookings.findById(100L)).thenReturn(Optional.of(new Booking(2L, 10L)));

        assertThatThrownBy(() -> service(Duration.ofHours(24)).cancel(1L, 100L, 10L))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void cancellationOfBookingOnUnknownEventFails() {
        when(events.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(Duration.ofHours(24)).cancel(99L, 100L, 10L))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void cancellationByAnotherUserLooksLikeAMissingBookingAndChangesNothing() {
        Event event = eventStartingAt(NOW.plus(Duration.ofDays(10)));
        Booking booking = activeBooking(event);
        int seatsBefore = event.getBookedCount();

        assertThatThrownBy(() -> service(Duration.ofHours(24)).cancel(1L, 100L, 999L))
                .isInstanceOf(BookingNotFoundException.class);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.ACTIVE);
        assertThat(event.getBookedCount()).isEqualTo(seatsBefore);
        verify(bookings, never()).saveAndFlush(any());
    }

    // ---------- helpers ----------

    private BookingService service(Duration notice) {
        return new BookingService(events, bookings, mock(PlatformTransactionManager.class),
                CLOCK, new BookingProperties(notice), mock(BookingEventPublisher.class));
    }

    /** Creates an event (id 1) with one seat already booked and registers it in the mock repository. */
    private Event eventStartingAt(Instant startsAt) {
        Event event = new Event("Jazz night", null, startsAt, 5, 1L);
        ReflectionTestUtils.setField(event, "id", 1L);
        event.book();
        when(events.findById(1L)).thenReturn(Optional.of(event));
        return event;
    }

    private Booking activeBooking(Event event) {
        Booking booking = new Booking(event.getId(), 10L);
        ReflectionTestUtils.setField(booking, "id", 100L);
        when(bookings.findById(100L)).thenReturn(Optional.of(booking));
        when(bookings.saveAndFlush(any(Booking.class))).thenAnswer(returnsFirstArg());
        return booking;
    }
}
