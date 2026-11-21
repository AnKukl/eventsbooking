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
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        repository = mock(EventRepository.class);
        eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        service = new EventService(repository, eventPublisher);
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
        when(repository.findByStatus(EventStatus.PUBLISHED, pageable)).thenReturn(page);

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

    // ---------- preview fields ----------

    private static EventRequest withPreview(String shortDescription, List<String> fit, List<String> notFit,
            List<MediaRequest> media) {
        return new EventRequest("Jazz night", "Live music", IN_A_WEEK, 50, shortDescription, fit, notFit, media);
    }

    @Test
    void createStoresThePreviewFields() {
        when(repository.save(any(Event.class))).thenAnswer(returnsFirstArg());

        Event created = service.create(withPreview("Pitch", List.of("a", "b"), List.of("c"),
                List.of(new MediaRequest("https://img/1.png", "Cover"))), 7L);

        assertThat(created.getShortDescription()).isEqualTo("Pitch");
        assertThat(created.getAudienceFit()).containsExactly("a", "b");
        assertThat(created.getAudienceNotFit()).containsExactly("c");
        assertThat(created.getMedia()).containsExactly(new EventMedia("https://img/1.png", "Cover"));
    }

    @Test
    void createWithoutPreviewFieldsLeavesThemEmpty() {
        when(repository.save(any(Event.class))).thenAnswer(returnsFirstArg());

        Event created = service.create(new EventRequest("Jazz night", null, IN_A_WEEK, 50), 7L);

        assertThat(created.getShortDescription()).isEmpty();
        assertThat(created.getAudienceFit()).isEmpty();
        assertThat(created.getMedia()).isEmpty();
    }

    @Test
    void updateReplacesThePreviewFieldsItIsGiven() {
        Event existing = new Event("Old", "old", IN_A_WEEK, 10, 1L);
        existing.applyPreview("Old pitch", List.of("a", "b", "c"), List.of("x"), List.of());
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        service.update(1L, withPreview("New pitch", List.of("z"), List.of(), null), 1L);

        assertThat(existing.getShortDescription()).isEqualTo("New pitch");
        assertThat(existing.getAudienceFit()).containsExactly("z");
        assertThat(existing.getAudienceNotFit()).isEmpty();
    }

    @Test
    void updateWithoutPreviewFieldsKeepsTheExistingOnes() {
        Event existing = new Event("Old", "old", IN_A_WEEK, 10, 1L);
        existing.applyPreview("Pitch", List.of("a"), List.of("x"), List.of(new EventMedia("https://img/1.png", null)));
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        service.update(1L, new EventRequest("Renamed", "old", IN_A_WEEK, 10), 1L);

        assertThat(existing.getTitle()).isEqualTo("Renamed");
        assertThat(existing.getShortDescription()).isEqualTo("Pitch");
        assertThat(existing.getAudienceFit()).containsExactly("a");
        assertThat(existing.getAudienceNotFit()).containsExactly("x");
        assertThat(existing.getMedia()).hasSize(1);
    }

    @Test
    void previewIsBuiltFromTheEvent() {
        Event existing = new Event("Old", "old", IN_A_WEEK, 10, 1L);
        existing.applyPreview("Pitch", List.of("a"), List.of("x"),
                List.of(new EventMedia("https://img/1.png", "Cover"), new EventMedia("https://img/2.png", null)));
        org.springframework.test.util.ReflectionTestUtils.setField(existing, "id", 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        EventPreviewResponse preview = service.preview(1L);

        assertThat(preview.id()).isEqualTo(1L);
        assertThat(preview.shortDescription()).isEqualTo("Pitch");
        assertThat(preview.audienceFit()).containsExactly("a");
        assertThat(preview.audienceNotFit()).containsExactly("x");
        assertThat(preview.media()).containsExactly(new MediaResponse("https://img/1.png", "Cover"),
                new MediaResponse("https://img/2.png", null));
    }

    @Test
    void previewOfAnUnknownEventThrowsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.preview(99L)).isInstanceOf(EventNotFoundException.class);
    }

    // ---------- SeatReleased on a capacity increase ----------

    private Event fullEvent(int capacity) {
        Event existing = new Event("Old", "old", IN_A_WEEK, capacity, 1L);
        for (int i = 0; i < capacity; i++) {
            existing.book();
        }
        org.springframework.test.util.ReflectionTestUtils.setField(existing, "id", 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        return existing;
    }

    @Test
    void raisingCapacityOfAFullEventReleasesTheAddedSeats() {
        fullEvent(10);

        service.update(1L, new EventRequest("Old", "old", IN_A_WEEK, 13), 1L);

        var captor = org.mockito.ArgumentCaptor.forClass(SeatReleased.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().eventId()).isEqualTo(1L);
        assertThat(captor.getValue().seats()).isEqualTo(3);
    }

    @Test
    void releasedSeatsEqualTheAddedCapacityEvenWhenTheEventWasNotFull() {
        Event existing = fullEvent(10);
        existing.release();
        existing.release();   // 8 of 10 booked

        service.update(1L, new EventRequest("Old", "old", IN_A_WEEK, 50), 1L);

        var captor = org.mockito.ArgumentCaptor.forClass(SeatReleased.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().seats()).isEqualTo(40);
    }

    @Test
    void sameOrLowerCapacityReleasesNothing() {
        Event existing = new Event("Old", "old", IN_A_WEEK, 10, 1L);
        org.springframework.test.util.ReflectionTestUtils.setField(existing, "id", 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        service.update(1L, new EventRequest("Renamed", "old", IN_A_WEEK, 10), 1L);
        service.update(1L, new EventRequest("Renamed", "old", IN_A_WEEK, 4), 1L);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void aDeniedUpdatePublishesNothing() {
        fullEvent(10);

        assertThatThrownBy(() -> service.update(1L, new EventRequest("Hijack", "x", IN_A_WEEK, 99), 2L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    // ---------- drafts and cancelled events are not public ----------

    @Test
    void aDraftIsNotFoundThroughThePublicReadPaths() {
        Event draft = new Event("Draft", null, IN_A_WEEK, 10, 7L);
        org.springframework.test.util.ReflectionTestUtils.setField(draft, "status", EventStatus.DRAFT);
        when(repository.findById(1L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.get(1L)).isInstanceOf(EventNotFoundException.class);
        assertThatThrownBy(() -> service.preview(1L)).isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void theOwnerCanStillChangeOrDeleteADraftThroughTheOldEndpoints() {
        Event draft = new Event("Draft", null, IN_A_WEEK, 10, 7L);
        org.springframework.test.util.ReflectionTestUtils.setField(draft, "status", EventStatus.DRAFT);
        when(repository.findById(1L)).thenReturn(Optional.of(draft));

        service.update(1L, new EventRequest("Renamed", null, IN_A_WEEK, 10), 7L);
        service.delete(1L, 7L);

        assertThat(draft.getTitle()).isEqualTo("Renamed");
        verify(repository).delete(draft);
    }
}
