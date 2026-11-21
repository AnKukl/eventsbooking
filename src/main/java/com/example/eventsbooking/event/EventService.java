package com.example.eventsbooking.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public EventService(EventRepository repository, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    /** The organizer is the authenticated user: it is never taken from the request body. */
    @Transactional
    public Event create(EventRequest request, long organizerId) {
        Event event = new Event(request.title(), request.description(), request.startsAt(),
                request.capacity(), organizerId);
        applyPreview(event, request);
        return repository.save(event);
    }

    /** The preview fields only; built inside the transaction because the lists are lazy. */
    public EventPreviewResponse preview(long id) {
        return EventPreviewResponse.from(get(id));
    }

    private static void applyPreview(Event event, EventRequest request) {
        event.applyPreview(request.shortDescription(), request.audienceFit(), request.audienceNotFit(),
                request.media() == null ? null
                        : request.media().stream().map(m -> new EventMedia(m.url(), m.alt())).toList());
    }

    public Event get(long id) {
        return repository.findById(id).orElseThrow(() -> new EventNotFoundException(id));
    }

    public Page<Event> list(Pageable pageable) {
        return repository.findAll(pageable);
    }

    /** Only the organizer who created the event may change it. */
    @Transactional
    public Event update(long id, EventRequest request, long requesterId) {
        Event event = ownedBy(id, requesterId);
        int capacityBefore = event.getCapacity();
        event.update(request.title(), request.description(), request.startsAt(), request.capacity(),
                event.getOrganizerId());
        applyPreview(event, request);
        int added = event.getCapacity() - capacityBefore;
        if (added > 0) {
            // delivered to the waitlist only if this transaction commits
            eventPublisher.publishEvent(new SeatReleased(id, UUID.randomUUID(), added));
        }
        return event;
    }

    @Transactional
    public void delete(long id, long requesterId) {
        repository.delete(ownedBy(id, requesterId));
    }

    private Event ownedBy(long id, long requesterId) {
        Event event = get(id);
        if (event.getOrganizerId() != requesterId) {
            throw new AccessDeniedException("Event " + id + " belongs to another organizer");
        }
        return event;
    }
}
