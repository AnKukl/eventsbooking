package com.example.eventsbooking.waitlist;

import com.example.eventsbooking.booking.BookingRepository;
import com.example.eventsbooking.booking.BookingStatus;
import com.example.eventsbooking.common.ConflictException;
import com.example.eventsbooking.event.Event;
import com.example.eventsbooking.event.EventNotFoundException;
import com.example.eventsbooking.event.EventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WaitlistServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    private final WaitlistRepository waitlist = mock(WaitlistRepository.class);
    private final EventRepository events = mock(EventRepository.class);
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final WaitlistService service =
            new WaitlistService(waitlist, events, bookings, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void joiningASoldOutEventCreatesTheEntryWithTheClockTime() {
        soldOutEvent(NOW.plusSeconds(86_400));
        when(waitlist.insertIfAbsent(7L, 5L, "PUSH", NOW)).thenReturn(1);

        assertThat(service.join(7L, 5L, WaitlistChannel.PUSH)).isTrue();

        verify(waitlist).insertIfAbsent(7L, 5L, "PUSH", NOW);
    }

    @Test
    void joiningTwiceIsIdempotentAndReportsNoCreation() {
        soldOutEvent(NOW.plusSeconds(86_400));
        when(waitlist.existsByUserIdAndEventId(7L, 5L)).thenReturn(true);

        assertThat(service.join(7L, 5L, WaitlistChannel.EMAIL)).isFalse();

        verify(waitlist, never()).insertIfAbsent(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void anExistingEntryStaysIdempotentEvenIfSeatsAppearedMeanwhile() {
        Event event = soldOutEvent(NOW.plusSeconds(86_400));
        event.release();
        when(waitlist.existsByUserIdAndEventId(7L, 5L)).thenReturn(true);

        assertThat(service.join(7L, 5L, WaitlistChannel.EMAIL)).isFalse();
    }

    @Test
    void aRacingInsertThatLostReportsNoCreation() {
        soldOutEvent(NOW.plusSeconds(86_400));
        when(waitlist.insertIfAbsent(7L, 5L, "EMAIL", NOW)).thenReturn(0);

        assertThat(service.join(7L, 5L, WaitlistChannel.EMAIL)).isFalse();
    }

    @Test
    void unknownEventIsNotFound() {
        when(events.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.join(7L, 99L, WaitlistChannel.EMAIL))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void anEventWithFreeSeatsCannotBeWaitedFor() {
        Event event = soldOutEvent(NOW.plusSeconds(86_400));
        event.release();

        assertThatThrownBy(() -> service.join(7L, 5L, WaitlistChannel.EMAIL))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("free seats");

        verify(waitlist, never()).insertIfAbsent(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void someoneWithAnActiveBookingCannotJoin() {
        soldOutEvent(NOW.plusSeconds(86_400));
        when(bookings.existsByEventIdAndUserIdAndStatusIn(5L, 7L, BookingStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> service.join(7L, 5L, WaitlistChannel.EMAIL))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already booked");

        verify(waitlist, never()).insertIfAbsent(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void aStartedEventCannotBeWaitedFor() {
        soldOutEvent(NOW.minusSeconds(1));

        assertThatThrownBy(() -> service.join(7L, 5L, WaitlistChannel.EMAIL))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("started");
    }

    /** Event 5 with a single seat that is taken. */
    private Event soldOutEvent(Instant startsAt) {
        Event event = new Event("Jazz night", null, startsAt, 1, 1L);
        event.book();
        ReflectionTestUtils.setField(event, "id", 5L);
        when(events.findById(5L)).thenReturn(Optional.of(event));
        return event;
    }

    @Test
    void aDraftCannotBeWaitedFor() {
        Event draft = soldOutEvent(NOW.plusSeconds(86_400));
        ReflectionTestUtils.setField(draft, "status", com.example.eventsbooking.event.EventStatus.DRAFT);

        assertThatThrownBy(() -> service.join(7L, 5L, WaitlistChannel.EMAIL)).isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void anEventWithTheWaitlistSwitchedOffRefusesNewEntries() {
        Event event = soldOutEvent(NOW.plusSeconds(86_400));
        event.organizerUpdate(null, null, null, null, null, null, false);

        assertThatThrownBy(() -> service.join(7L, 5L, WaitlistChannel.EMAIL))
                .isInstanceOf(ConflictException.class).hasMessageContaining("waitlist");

        verify(waitlist, never()).insertIfAbsent(anyLong(), anyLong(), anyString(), any());
    }
}
