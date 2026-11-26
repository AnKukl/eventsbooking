package com.example.eventsbooking.organizer;

import com.example.eventsbooking.common.ConflictException;
import com.example.eventsbooking.event.Event;
import com.example.eventsbooking.event.EventNotFoundException;
import com.example.eventsbooking.event.EventRepository;
import com.example.eventsbooking.event.EventStatus;
import com.example.eventsbooking.event.MissingField;
import com.example.eventsbooking.event.SeatReleased;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * The organizer's view of their own events. Every method takes the id of the authenticated organizer and checks that the
 * event is theirs: the role alone ({@code ORGANIZER}) is not enough, an organizer must not touch somebody else's event.
 */
@Service
@Transactional(readOnly = true)
public class OrganizerEventService {

    private final EventRepository events;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public OrganizerEventService(EventRepository events, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.events = events;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public Page<OrganizerEventResponse> list(long organizerId, OrganizerEventStatus status, Pageable pageable) {
        Page<Event> page;
        if (status == null) {
            page = events.findByOrganizerId(organizerId, pageable);
        } else {
            page = switch (status) {
                case DRAFT -> events.findByOrganizerIdAndStatus(organizerId, EventStatus.DRAFT, pageable);
                case CANCELLED -> events.findByOrganizerIdAndStatus(organizerId, EventStatus.CANCELLED, pageable);
                case PUBLISHED -> events.findPublishedWithFreeSeats(organizerId, pageable);
                case SOLD_OUT -> events.findSoldOut(organizerId, pageable);
            };
        }
        return page.map(OrganizerEventResponse::from);
    }

    public OrganizerEventResponse get(long eventId, long organizerId) {
        return OrganizerEventResponse.from(owned(eventId, organizerId));
    }

    @Transactional
    public OrganizerEventResponse create(OrganizerEventCreateRequest request, long organizerId) {
        Event event = Event.draft(request.title(), request.description(), request.startsAt(), request.capacity(),
                organizerId);
        event.organizerUpdate(null, null, null, null, request.venue(), request.price(), request.waitlistEnabled());
        event.locate(request.latitude(), request.longitude());
        event.applyPreview(request.shortDescription(), request.audienceFit(), request.audienceNotFit(), null);
        return OrganizerEventResponse.from(events.save(event));
    }

    /**
     * Partial update against a version: a request carrying an older version than the stored one is refused (409), so
     * two open forms cannot silently overwrite each other. Fields that are not sent stay as they are.
     */
    @Transactional
    public OrganizerEventResponse update(long eventId, OrganizerEventUpdateRequest request, long organizerId) {
        Event event = owned(eventId, organizerId);
        if (request.version() != event.getRevision()) {
            throw new StaleEventVersionException(eventId);
        }
        int capacityBefore = event.getCapacity();
        event.organizerUpdate(request.title(), request.description(), request.startsAt(), request.capacity(),
                request.venue(), request.price(), request.waitlistEnabled());
        event.locate(request.latitude(), request.longitude());
        event.applyPreview(request.shortDescription(), request.audienceFit(), request.audienceNotFit(), null);
        event.bumpRevision();
        int added = event.getCapacity() - capacityBefore;
        if (added > 0 && event.getStatus() == EventStatus.PUBLISHED) {
            // delivered to the waitlist only if this transaction commits
            eventPublisher.publishEvent(new SeatReleased(eventId, UUID.randomUUID(), added));
        }
        return OrganizerEventResponse.from(event);
    }

    /**
     * Makes a complete draft public. Publishing something already published changes nothing. An incomplete event is not
     * published and the exception lists every field still missing.
     */
    @Transactional
    public OrganizerEventResponse publish(long eventId, long organizerId) {
        Event event = owned(eventId, organizerId);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new ConflictException("Event cancelled", "A cancelled event cannot be published");
        }
        if (event.getStatus() == EventStatus.PUBLISHED) {
            return OrganizerEventResponse.from(event);
        }
        List<MissingField> missing = event.missingForPublish(clock.instant());
        if (!missing.isEmpty()) {
            throw new EventNotPublishableException(missing);
        }
        event.publish();
        event.bumpRevision();
        return OrganizerEventResponse.from(event);
    }

    private Event owned(long eventId, long organizerId) {
        Event event = events.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
        if (event.getOrganizerId() != organizerId) {
            throw new AccessDeniedException("Event " + eventId + " belongs to another organizer");
        }
        return event;
    }
}
