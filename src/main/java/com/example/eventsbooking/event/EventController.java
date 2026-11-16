package com.example.eventsbooking.event;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/events")
class EventController {

    private final EventService service;

    EventController(EventService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<EventResponse> create(@Valid @RequestBody EventRequest request) {
        Event created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(EventResponse.from(created));
    }

    @GetMapping("/{id}")
    EventResponse get(@PathVariable long id) {
        return EventResponse.from(service.get(id));
    }

    @GetMapping
    PagedModel<EventResponse> list(@PageableDefault(size = 20, sort = "startsAt") Pageable pageable) {
        return new PagedModel<>(service.list(pageable).map(EventResponse::from));
    }

    @PutMapping("/{id}")
    EventResponse update(@PathVariable long id, @Valid @RequestBody EventRequest request) {
        return EventResponse.from(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
