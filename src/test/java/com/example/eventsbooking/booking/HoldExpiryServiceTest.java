package com.example.eventsbooking.booking;

import com.example.eventsbooking.event.Event;
import com.example.eventsbooking.event.EventRepository;
import com.example.eventsbooking.event.SeatReleased;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HoldExpiryServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    private final BookingRepository bookings = mock(BookingRepository.class);
    private final EventRepository events = mock(EventRepository.class);
    private final ApplicationEventPublisher spring = mock(ApplicationEventPublisher.class);
    private final HoldExpiryService service =
            new HoldExpiryService(bookings, events, spring, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void expiredHoldsAreCancelledAndTheirSeatsGoBackToTheEvent() {
        Event event = event(1L, 10);
        event.book(3);
        Booking first = hold(1L, 1L, 2);
        Booking second = hold(2L, 1L, 1);
        when(bookings.lockExpiredHolds(NOW, HoldExpiryService.BATCH_SIZE)).thenReturn(List.of(first, second));

        int expired = service.expireHolds();

        assertThat(expired).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(first.getCancellationReason()).isEqualTo("HOLD_EXPIRED");
        assertThat(first.getCancelledAt()).isEqualTo(NOW);
        assertThat(second.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(event.getBookedCount()).isZero();
    }

    @Test
    void theWaitlistHearsOnceAboutAllSeatsFreedOnAnEvent() {
        Event event = event(1L, 10);
        event.book(3);
        when(bookings.lockExpiredHolds(NOW, HoldExpiryService.BATCH_SIZE))
                .thenReturn(List.of(hold(1L, 1L, 2), hold(2L, 1L, 1)));

        service.expireHolds();

        ArgumentCaptor<SeatReleased> released = ArgumentCaptor.forClass(SeatReleased.class);
        verify(spring).publishEvent(released.capture());
        assertThat(released.getValue().eventId()).isEqualTo(1L);
        assertThat(released.getValue().seats()).isEqualTo(3);
    }

    @Test
    void eachEventGetsItsOwnRelease() {
        event(1L, 10).book(1);
        event(2L, 10).book(2);
        when(bookings.lockExpiredHolds(NOW, HoldExpiryService.BATCH_SIZE))
                .thenReturn(List.of(hold(1L, 1L, 1), hold(2L, 2L, 2)));

        service.expireHolds();

        verify(spring, times(2)).publishEvent(any(SeatReleased.class));
    }

    @Test
    void nothingExpiredMeansNothingHappens() {
        when(bookings.lockExpiredHolds(NOW, HoldExpiryService.BATCH_SIZE)).thenReturn(List.of());

        assertThat(service.expireHolds()).isZero();

        verifyNoInteractions(events);
        verifyNoInteractions(spring);
    }

    private Event event(long id, int capacity) {
        Event event = new Event("Jazz night", null, NOW.plus(Duration.ofDays(5)), capacity, 1L);
        ReflectionTestUtils.setField(event, "id", id);
        when(events.findById(id)).thenReturn(Optional.of(event));
        return event;
    }

    private static Booking hold(long id, long eventId, int seats) {
        Booking booking = new Booking(eventId, 10L + id, seats, NOW.minus(Duration.ofMinutes(11)),
                NOW.minus(Duration.ofMinutes(1)));
        ReflectionTestUtils.setField(booking, "id", id);
        return booking;
    }
}
