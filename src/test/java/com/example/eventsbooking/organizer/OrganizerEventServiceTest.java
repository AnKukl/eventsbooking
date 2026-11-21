package com.example.eventsbooking.organizer;

import com.example.eventsbooking.common.ConflictException;
import com.example.eventsbooking.event.Event;
import com.example.eventsbooking.event.EventMedia;
import com.example.eventsbooking.event.EventNotFoundException;
import com.example.eventsbooking.event.EventRepository;
import com.example.eventsbooking.event.EventStatus;
import com.example.eventsbooking.event.SeatReleased;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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

class OrganizerEventServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private static final Instant LATER = NOW.plus(Duration.ofDays(30));

    private final EventRepository events = mock(EventRepository.class);
    private final ApplicationEventPublisher spring = mock(ApplicationEventPublisher.class);
    private final OrganizerEventService service =
            new OrganizerEventService(events, spring, Clock.fixed(NOW, ZoneOffset.UTC));

    // ---------- create ----------

    @Test
    void aNewEventIsADraftOwnedByTheCallerWithTheWaitlistOnByDefault() {
        when(events.save(any(Event.class))).thenAnswer(returnsFirstArg());

        OrganizerEventResponse created = service.create(new OrganizerEventCreateRequest("Jazz night", "Live", LATER, 50,
                null, null, null, null, null, null), 7L);

        assertThat(created.status()).isEqualTo(OrganizerEventStatus.DRAFT);
        assertThat(created.title()).isEqualTo("Jazz night");
        assertThat(created.capacity()).isEqualTo(50);
        assertThat(created.waitlistEnabled()).isTrue();
        assertThat(created.version()).isZero();
        assertThat(created.venue()).isNull();
        assertThat(created.price()).isNull();
        var captor = ArgumentCaptor.forClass(Event.class);
        verify(events).save(captor.capture());
        assertThat(captor.getValue().getOrganizerId()).isEqualTo(7L);
        assertThat(captor.getValue().getStatus()).isEqualTo(EventStatus.DRAFT);
    }

    @Test
    void createStoresTheOptionalFields() {
        when(events.save(any(Event.class))).thenAnswer(returnsFirstArg());

        OrganizerEventResponse created = service.create(new OrganizerEventCreateRequest("Jazz", null, LATER, 10,
                "Short pitch", "Main hall", new BigDecimal("19.90"), false, List.of("Fans"), List.of("Kids")), 7L);

        assertThat(created.shortDescription()).isEqualTo("Short pitch");
        assertThat(created.venue()).isEqualTo("Main hall");
        assertThat(created.price()).isEqualByComparingTo("19.90");
        assertThat(created.waitlistEnabled()).isFalse();
        assertThat(created.audienceFit()).containsExactly("Fans");
        assertThat(created.audienceNotFit()).containsExactly("Kids");
    }

    // ---------- ownership: checked in the service, not only by the role ----------

    @Test
    void everyOperationOnAnotherOrganizersEventIsForbidden() {
        event(1L, EventStatus.DRAFT);

        assertThatThrownBy(() -> service.get(5L, 99L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.update(5L, update(0L), 99L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.publish(5L, 99L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anUnknownEventIsNotFound() {
        when(events.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(404L, 7L)).isInstanceOf(EventNotFoundException.class);
    }

    // ---------- update with a version ----------

    @Test
    void aMatchingVersionAppliesOnlyTheGivenFieldsAndBumpsTheVersion() {
        Event event = event(7L, EventStatus.DRAFT);

        OrganizerEventResponse updated = service.update(5L, new OrganizerEventUpdateRequest(0L, "Renamed", null, null,
                null, null, "Hall A", new BigDecimal("10"), null, null, null), 7L);

        assertThat(updated.title()).isEqualTo("Renamed");
        assertThat(updated.venue()).isEqualTo("Hall A");
        assertThat(updated.price()).isEqualByComparingTo("10");
        assertThat(updated.capacity()).as("untouched").isEqualTo(20);
        assertThat(updated.version()).isEqualTo(1L);
        assertThat(event.getRevision()).isEqualTo(1L);
    }

    @Test
    void aStaleVersionIsAConflictAndChangesNothing() {
        Event event = event(7L, EventStatus.DRAFT);
        service.update(5L, update(0L), 7L);   // someone else saved first: the event is at version 1 now

        assertThatThrownBy(() -> service.update(5L, new OrganizerEventUpdateRequest(0L, "Mine", null, null, null, null,
                null, null, null, null, null), 7L)).isInstanceOf(StaleEventVersionException.class);

        assertThat(event.getTitle()).isEqualTo("Jazz");
        assertThat(event.getRevision()).isEqualTo(1L);
    }

    @Test
    void capacityBelowTheBookedSeatsIsAConflict() {
        Event event = event(7L, EventStatus.PUBLISHED);
        event.book(5);

        assertThatThrownBy(() -> service.update(5L, new OrganizerEventUpdateRequest(0L, null, null, null, 3, null,
                null, null, null, null, null), 7L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void raisingTheCapacityOfAPublishedEventReleasesTheNewSeatsToTheWaitlist() {
        event(7L, EventStatus.PUBLISHED);

        service.update(5L, new OrganizerEventUpdateRequest(0L, null, null, null, 23, null, null, null, null, null, null), 7L);

        var captor = ArgumentCaptor.forClass(SeatReleased.class);
        verify(spring).publishEvent(captor.capture());
        assertThat(captor.getValue().eventId()).isEqualTo(5L);
        assertThat(captor.getValue().seats()).isEqualTo(3);
    }

    @Test
    void aDraftHasNoWaitlistToTell() {
        event(7L, EventStatus.DRAFT);

        service.update(5L, new OrganizerEventUpdateRequest(0L, null, null, null, 30, null, null, null, null, null, null), 7L);

        verify(spring, never()).publishEvent(any(Object.class));
    }

    // ---------- publish ----------

    @Test
    void publishingAnIncompleteEventListsTheMissingFields() {
        Event event = event(7L, EventStatus.DRAFT);

        assertThatThrownBy(() -> service.publish(5L, 7L))
                .isInstanceOfSatisfying(EventNotPublishableException.class, e -> assertThat(e.getMissing())
                        .extracting(missing -> missing.field()).containsExactly("venue", "price", "media"));

        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
    }

    @Test
    void aPastDateAndABlankTitleAreMissingToo() {
        Event event = new Event("  ", null, NOW.minusSeconds(1), 20, 7L);
        event.organizerUpdate(null, null, null, null, "Hall", new BigDecimal("5"), null);
        event.applyPreview(null, null, null, List.of(new EventMedia("/api/media/a.png", null)));
        ReflectionTestUtils.setField(event, "id", 5L);
        ReflectionTestUtils.setField(event, "status", EventStatus.DRAFT);
        when(events.findById(5L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.publish(5L, 7L))
                .isInstanceOfSatisfying(EventNotPublishableException.class, e -> assertThat(e.getMissing())
                        .extracting(missing -> missing.field()).containsExactly("title", "startsAt"));
    }

    @Test
    void aFreeEventHasAPriceOfZeroAndMayBePublished() {
        Event event = completeDraft();
        event.organizerUpdate(null, null, null, null, null, BigDecimal.ZERO, null);

        assertThat(service.publish(5L, 7L).status()).isEqualTo(OrganizerEventStatus.PUBLISHED);
    }

    @Test
    void aCompleteDraftIsPublished() {
        Event event = completeDraft();

        OrganizerEventResponse published = service.publish(5L, 7L);

        assertThat(published.status()).isEqualTo(OrganizerEventStatus.PUBLISHED);
        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void publishingTwiceIsHarmless() {
        completeDraft();

        service.publish(5L, 7L);

        assertThat(service.publish(5L, 7L).status()).isEqualTo(OrganizerEventStatus.PUBLISHED);
    }

    @Test
    void aCancelledEventCannotBePublished() {
        event(7L, EventStatus.CANCELLED);

        assertThatThrownBy(() -> service.publish(5L, 7L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void aFullPublishedEventIsReportedAsSoldOut() {
        Event event = event(7L, EventStatus.PUBLISHED);
        event.book(20);

        assertThat(service.get(5L, 7L).status()).isEqualTo(OrganizerEventStatus.SOLD_OUT);
    }

    // ---------- listing ----------

    @Test
    void theListIsFilteredByTheEffectiveStatusOfTheCallersEvents() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Event> empty = new PageImpl<>(List.of());
        when(events.findByOrganizerId(7L, pageable)).thenReturn(empty);
        when(events.findByOrganizerIdAndStatus(7L, EventStatus.DRAFT, pageable)).thenReturn(empty);
        when(events.findByOrganizerIdAndStatus(7L, EventStatus.CANCELLED, pageable)).thenReturn(empty);
        when(events.findPublishedWithFreeSeats(7L, pageable)).thenReturn(empty);
        when(events.findSoldOut(7L, pageable)).thenReturn(empty);

        service.list(7L, null, pageable);
        service.list(7L, OrganizerEventStatus.DRAFT, pageable);
        service.list(7L, OrganizerEventStatus.CANCELLED, pageable);
        service.list(7L, OrganizerEventStatus.PUBLISHED, pageable);
        service.list(7L, OrganizerEventStatus.SOLD_OUT, pageable);

        verify(events).findByOrganizerId(7L, pageable);
        verify(events).findByOrganizerIdAndStatus(7L, EventStatus.DRAFT, pageable);
        verify(events).findByOrganizerIdAndStatus(7L, EventStatus.CANCELLED, pageable);
        verify(events).findPublishedWithFreeSeats(7L, pageable);
        verify(events).findSoldOut(7L, pageable);
    }

    // ---------- helpers ----------

    private static OrganizerEventUpdateRequest update(long version) {
        return new OrganizerEventUpdateRequest(version, null, null, null, null, null, null, null, null, null, null);
    }

    /** Event 5 (capacity 20) of the given organizer, registered in the mock repository. */
    private Event event(long organizerId, EventStatus status) {
        Event event = new Event("Jazz", null, LATER, 20, organizerId);
        ReflectionTestUtils.setField(event, "id", 5L);
        ReflectionTestUtils.setField(event, "status", status);
        when(events.findById(5L)).thenReturn(Optional.of(event));
        return event;
    }

    private Event completeDraft() {
        Event event = event(7L, EventStatus.DRAFT);
        event.organizerUpdate(null, null, null, null, "Main hall", new BigDecimal("15.00"), null);
        event.applyPreview(null, null, null, List.of(new EventMedia("/api/media/a.png", null)));
        return event;
    }
}
