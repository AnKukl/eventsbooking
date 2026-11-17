package com.example.eventsbooking.event;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository repository;

    public EventService(EventRepository repository) {
        this.repository = repository;
    }

    /** The organizer is the authenticated user: it is never taken from the request body. */
    @Transactional
    public Event create(EventRequest request, long organizerId) {
        return repository.save(new Event(request.title(), request.description(), request.startsAt(),
                request.capacity(), organizerId));
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
        event.update(request.title(), request.description(), request.startsAt(), request.capacity(),
                event.getOrganizerId());
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
