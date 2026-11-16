package com.example.eventsbooking.booking;

import com.example.eventsbooking.event.Event;
import com.example.eventsbooking.event.EventFullException;
import com.example.eventsbooking.event.EventNotFoundException;
import com.example.eventsbooking.event.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingServiceTest {

    private EventRepository events;
    private BookingRepository bookings;
    private BookingService service;

    @BeforeEach
    void setUp() {
        events = mock(EventRepository.class);
        bookings = mock(BookingRepository.class);
        service = new BookingService(events, bookings, mock(PlatformTransactionManager.class));
    }

    @Test
    void bookSavesBookingAndIncrementsBookedCount() {
        Event event = event(2);
        when(events.findById(1L)).thenReturn(Optional.of(event));
        when(bookings.saveAndFlush(any(Booking.class))).thenAnswer(returnsFirstArg());

        Booking booking = service.book(1L, 10L);

        assertThat(booking.getEventId()).isEqualTo(1L);
        assertThat(booking.getUserId()).isEqualTo(10L);
        assertThat(event.getBookedCount()).isEqualTo(1);
    }

    @Test
    void bookThrowsWhenEventDoesNotExist() {
        when(events.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.book(99L, 10L)).isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void bookThrowsWhenUserAlreadyBookedTheEvent() {
        when(events.findById(1L)).thenReturn(Optional.of(event(5)));
        when(bookings.existsByEventIdAndUserId(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.book(1L, 10L)).isInstanceOf(AlreadyBookedException.class);
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void bookThrowsWhenEventIsFull() {
        Event full = event(1);
        full.book();
        when(events.findById(1L)).thenReturn(Optional.of(full));

        assertThatThrownBy(() -> service.book(1L, 10L)).isInstanceOf(EventFullException.class);
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void bookRetriesAfterOptimisticLockFailure() {
        when(events.findById(1L)).thenAnswer(invocation -> Optional.of(event(5)));
        when(bookings.saveAndFlush(any(Booking.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Event.class, 1L))
                .thenAnswer(returnsFirstArg());

        Booking booking = service.book(1L, 10L);

        assertThat(booking.getUserId()).isEqualTo(10L);
        verify(bookings, times(2)).saveAndFlush(any());
    }

    @Test
    void bookGivesUpAfterMaxAttempts() {
        when(events.findById(1L)).thenAnswer(invocation -> Optional.of(event(5)));
        when(bookings.saveAndFlush(any(Booking.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Event.class, 1L));

        assertThatThrownBy(() -> service.book(1L, 10L)).isInstanceOf(ConcurrentBookingException.class);
        verify(bookings, times(BookingService.MAX_ATTEMPTS)).saveAndFlush(any());
    }

    @Test
    void uniqueConstraintViolationIsReportedAsAlreadyBooked() {
        when(events.findById(1L)).thenAnswer(invocation -> Optional.of(event(5)));
        when(bookings.existsByEventIdAndUserId(1L, 10L)).thenReturn(false, true);
        when(bookings.saveAndFlush(any(Booking.class)))
                .thenThrow(new DataIntegrityViolationException("uq_bookings_event_user"));

        assertThatThrownBy(() -> service.book(1L, 10L)).isInstanceOf(AlreadyBookedException.class);
    }

    private static Event event(int capacity) {
        Event event = new Event("Jazz night", null, Instant.now().plus(7, ChronoUnit.DAYS), capacity, 1L);
        ReflectionTestUtils.setField(event, "id", 1L);
        return event;
    }
}
