package com.example.eventsbooking.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventServiceTest {

    private static final Instant IN_A_WEEK = Instant.now().plus(7, ChronoUnit.DAYS);

    private EventRepository repository;
    private EventService service;

    @BeforeEach
    void setUp() {
        repository = mock(EventRepository.class);
        service = new EventService(repository);
    }

    @Test
    void createPersistsEventWithAllFields() {
        when(repository.save(any(Event.class))).thenAnswer(returnsFirstArg());

        Event created = service.create(new EventRequest("Jazz night", "Live music", IN_A_WEEK, 50), 7L);

        assertThat(created.getTitle()).isEqualTo("Jazz night");
        assertThat(created.getDescription()).isEqualTo("Live music");
        assertThat(created.getStartsAt()).isEqualTo(IN_A_WEEK);
        assertThat(created.getCapacity()).isEqualTo(50);
        assertThat(created.getOrganizerId()).isEqualTo(7L);
        verify(repository).save(created);
    }

    @Test
    void getReturnsExistingEvent() {
        Event event = new Event("Jazz night", null, IN_A_WEEK, 50, 7L);
        when(repository.findById(1L)).thenReturn(Optional.of(event));

        assertThat(service.get(1L)).isSameAs(event);
    }

    @Test
    void getThrowsWhenEventDoesNotExist() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOf(EventNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void listDelegatesPageableToRepository() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Event> page = new PageImpl<>(
                List.of(new Event("Jazz night", null, IN_A_WEEK, 50, 7L)), pageable, 1);
        when(repository.findAll(pageable)).thenReturn(page);

        assertThat(service.list(pageable)).isSameAs(page);
    }

    @Test
    void updateReplacesAllFieldsOfExistingEvent() {
        Event existing = new Event("Old", "old", IN_A_WEEK, 10, 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        Instant later = IN_A_WEEK.plus(1, ChronoUnit.DAYS);

        Event updated = service.update(1L, new EventRequest("New", "new", later, 20), 1L);

        assertThat(updated.getTitle()).isEqualTo("New");
        assertThat(updated.getDescription()).isEqualTo("new");
        assertThat(updated.getStartsAt()).isEqualTo(later);
        assertThat(updated.getCapacity()).isEqualTo(20);
        assertThat(updated.getOrganizerId()).as("owner never changes").isEqualTo(1L);
    }

    @Test
    void updateThrowsWhenEventDoesNotExist() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, new EventRequest("T", null, IN_A_WEEK, 1), 1L))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void updateRejectsCapacityBelowAlreadyBookedSeats() {
        Event existing = new Event("Old", null, IN_A_WEEK, 5, 1L);
        existing.book();
        existing.book();
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(1L, new EventRequest("Old", null, IN_A_WEEK, 1), 1L))
                .isInstanceOf(com.example.eventsbooking.common.ConflictException.class);
        assertThat(existing.getCapacity()).isEqualTo(5);
    }

    @Test
    void deleteRemovesTheEventOfItsOrganizer() {
        Event existing = new Event("Old", null, IN_A_WEEK, 5, 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        service.delete(1L, 1L);

        verify(repository).delete(existing);
    }

    @Test
    void deleteThrowsWhenEventDoesNotExist() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L, 1L)).isInstanceOf(EventNotFoundException.class);
        verify(repository, never()).delete(any());
    }

    // ---------- ownership: only the organizer who created an event may change it ----------

    @Test
    void updateByAnotherOrganizerIsDeniedAndChangesNothing() {
        Event existing = new Event("Old", "old", IN_A_WEEK, 10, 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(1L, new EventRequest("Hijacked", "x", IN_A_WEEK, 99), 2L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        assertThat(existing.getTitle()).isEqualTo("Old");
        assertThat(existing.getCapacity()).isEqualTo(10);
    }

    @Test
    void deleteByAnotherOrganizerIsDeniedAndDeletesNothing() {
        Event existing = new Event("Old", null, IN_A_WEEK, 5, 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.delete(1L, 2L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verify(repository, never()).delete(any());
    }
}
