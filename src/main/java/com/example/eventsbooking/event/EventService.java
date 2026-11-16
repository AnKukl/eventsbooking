package com.example.eventsbooking.event;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository repository;

    public EventService(EventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Event create(EventRequest request) {
        return repository.save(new Event(request.title(), request.description(), request.startsAt(),
                request.capacity(), request.organizerId()));
    }

    public Event get(long id) {
        return repository.findById(id).orElseThrow(() -> new EventNotFoundException(id));
    }

    public Page<Event> list(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional
    public Event update(long id, EventRequest request) {
        Event event = get(id);
        event.update(request.title(), request.description(), request.startsAt(),
                request.capacity(), request.organizerId());
        return event;
    }

    @Transactional
    public void delete(long id) {
        if (!repository.existsById(id)) {
            throw new EventNotFoundException(id);
        }
        repository.deleteById(id);
    }
}
