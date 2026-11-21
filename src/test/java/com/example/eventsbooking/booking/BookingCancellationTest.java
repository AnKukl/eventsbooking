package com.example.eventsbooking.booking;

import com.example.eventsbooking.common.ConflictException;
import com.example.eventsbooking.event.Event;
import com.example.eventsbooking.event.EventRepository;
import com.example.eventsbooking.event.SeatReleased;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The 24-hour rule. Time comes only from a fixed {@link Clock}: no sleeping, no real "now". */
class BookingCancellationTest {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private static final Duration NOTICE = Duration.ofHours(24);

    private final EventRepository events = mock(EventRepository.class);
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final ApplicationEventPublisher spring = mock(ApplicationEventPublisher.class);
    private final PaymentGateway payments = mock(PaymentGateway.class);
    private final BookingService service = new BookingService(events, bookings,
            mock(PlatformTransactionManager.class), Clock.fixed(NOW, ZoneOffset.UTC),
            new BookingProperties(NOTICE, 4, Duration.ofMinutes(10)), mock(BookingEventPublisher.class), spring,
            payments, mock(IdempotencyKeys.class));

    // ---------- the boundary: 24 hours and more means a refund ----------

    @Test
    void exactly24HoursBeforeTheStartTheBookingIsRefundable() {
        Booking booking = confirmedBookingOfAnEventStartingAt(NOW.plus(NOTICE));

        Booking cancelled = service.cancel(100L, 10L, CancellationReason.CHANGED_PLANS);

        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.REFUND_PENDING);
        assertThat(cancelled.getCancellationReason()).isEqualTo("CHANGED_PLANS");
        assertThat(cancelled.getCancelledAt()).isEqualTo(NOW);
        verify(payments).refund(booking);
    }

    @Test
    void oneNanosecondShortOf24HoursThereIsNoRefund() {
        confirmedBookingOfAnEventStartingAt(NOW.plus(NOTICE).minusNanos(1));

        Booking cancelled = service.cancel(100L, 10L, CancellationReason.CHANGED_PLANS);

        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verifyNoInteractions(payments);
    }

    @Test
    void at23Hours59MinutesBeforeTheStartThereIsNoRefundButTheCancellationWorks() {
        Booking booking = confirmedBookingOfAnEventStartingAt(NOW.plus(Duration.ofHours(23).plusMinutes(59)));

        service.cancel(100L, 10L, CancellationReason.OTHER);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verifyNoInteractions(payments);
    }

    @Test
    void at24Hours1MinuteBeforeTheStartTheBookingIsRefundable() {
        confirmedBookingOfAnEventStartingAt(NOW.plus(NOTICE).plus(Duration.ofMinutes(1)));

        assertThat(service.cancel(100L, 10L, CancellationReason.OTHER).getStatus())
                .isEqualTo(BookingStatus.REFUND_PENDING);
    }

    // ---------- a hold has not been paid for: nothing to refund, whatever the time ----------

    @Test
    void cancellingAHoldNeverRefundsEvenFarBeforeTheStart() {
        Booking hold = holdOfAnEventStartingAt(NOW.plus(Duration.ofDays(30)));

        service.cancel(100L, 10L, CancellationReason.CHANGED_PLANS);

        assertThat(hold.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verifyNoInteractions(payments);
    }

    // ---------- the seats come back ----------

    @Test
    void allSeatsOfTheBookingAreReleasedAndTheWaitlistIsTold() {
        Event event = eventStartingAt(NOW.plus(Duration.ofDays(5)), 10);
        event.book(3);
        bookingOf(event, BookingStatus.CONFIRMED, 3);

        service.cancel(100L, 10L, CancellationReason.OTHER);

        assertThat(event.getBookedCount()).isZero();
        ArgumentCaptor<SeatReleased> released = ArgumentCaptor.forClass(SeatReleased.class);
        verify(spring).publishEvent(released.capture());
        assertThat(released.getValue().eventId()).isEqualTo(1L);
        assertThat(released.getValue().seats()).isEqualTo(3);
    }

    // ---------- refused cancellations change nothing ----------

    @Test
    void cancellingTwiceIsAConflictAndReleasesNothingTwice() {
        Event event = eventStartingAt(NOW.plus(Duration.ofDays(5)), 10);
        event.book(1);
        Booking booking = bookingOf(event, BookingStatus.CONFIRMED, 1);

        service.cancel(100L, 10L, CancellationReason.OTHER);
        assertThatThrownBy(() -> service.cancel(100L, 10L, CancellationReason.OTHER))
                .isInstanceOf(BookingAlreadyCancelledException.class);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.REFUND_PENDING);
        verify(spring, org.mockito.Mockito.times(1)).publishEvent(any(Object.class));
        verify(payments, org.mockito.Mockito.times(1)).refund(booking);
    }

    @Test
    void aBookingWhoseRefundIsPendingCannotBeCancelledAgain() {
        Event event = eventStartingAt(NOW.plus(Duration.ofDays(5)), 10);
        Booking booking = bookingOf(event, BookingStatus.CONFIRMED, 1);
        booking.cancel(NOW, "OTHER", true);

        assertThatThrownBy(() -> service.cancel(100L, 10L, CancellationReason.OTHER))
                .isInstanceOf(BookingAlreadyCancelledException.class);
    }

    @Test
    void someoneElsesBookingLooksLikeAMissingOne() {
        confirmedBookingOfAnEventStartingAt(NOW.plus(Duration.ofDays(5)));

        assertThatThrownBy(() -> service.cancel(100L, 99L, CancellationReason.OTHER))
                .isInstanceOf(BookingNotFoundException.class);

        verifyNoInteractions(payments);
    }

    @Test
    void aStartedEventCannotBeCancelled() {
        Booking booking = confirmedBookingOfAnEventStartingAt(NOW.minusSeconds(1));

        assertThatThrownBy(() -> service.cancel(100L, 10L, CancellationReason.OTHER))
                .isInstanceOf(ConflictException.class).hasMessageContaining("started");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(spring, never()).publishEvent(any(Object.class));
    }

    // ---------- the payment gateway may fail without undoing the cancellation ----------

    @Test
    void aFailingRefundCallKeepsTheCancellationAndLeavesTheRefundPending() {
        Booking booking = confirmedBookingOfAnEventStartingAt(NOW.plus(Duration.ofDays(5)));
        doThrow(new IllegalStateException("gateway down")).when(payments).refund(booking);

        Booking cancelled = service.cancel(100L, 10L, CancellationReason.OTHER);

        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.REFUND_PENDING);
    }

    // ---------- helpers ----------

    private Event eventStartingAt(Instant startsAt, int capacity) {
        Event event = new Event("Jazz night", null, startsAt, capacity, 1L);
        ReflectionTestUtils.setField(event, "id", 1L);
        when(events.findById(1L)).thenReturn(Optional.of(event));
        return event;
    }

    private Booking bookingOf(Event event, BookingStatus status, int seats) {
        Booking booking = new Booking(1L, 10L, seats, NOW.minusSeconds(3600), NOW.plus(Duration.ofMinutes(5)));
        ReflectionTestUtils.setField(booking, "id", 100L);
        if (status == BookingStatus.CONFIRMED) {
            booking.confirm(NOW.minusSeconds(3000));
        }
        when(bookings.findById(100L)).thenReturn(Optional.of(booking));
        when(bookings.saveAndFlush(any(Booking.class))).thenAnswer(returnsFirstArg());
        return booking;
    }

    private Booking confirmedBookingOfAnEventStartingAt(Instant startsAt) {
        Event event = eventStartingAt(startsAt, 10);
        event.book(1);
        return bookingOf(event, BookingStatus.CONFIRMED, 1);
    }

    private Booking holdOfAnEventStartingAt(Instant startsAt) {
        Event event = eventStartingAt(startsAt, 10);
        event.book(1);
        return bookingOf(event, BookingStatus.HOLD, 1);
    }
}
