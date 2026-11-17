package com.example.eventsbooking.event;

import jakarta.validation.Valid;
import java.security.Principal;
import com.example.eventsbooking.security.CurrentUser;
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
    ResponseEntity<EventResponse> create(@Valid @RequestBody EventRequest request, Principal principal) {
        Event created = service.create(request, CurrentUser.id(principal));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(EventResponse.from(created));
    }

    @GetMapping("/{id}")
    EventResponse get(@PathVariable long id) {
        return EventResponse.from(service.get(id));
    }

    /** Only public, indexed columns may be used for sorting; internal fields (version, organizerId, ...) may not. */
    private static final java.util.Set<String> SORTABLE = java.util.Set.of("id", "title", "startsAt", "capacity");

    @GetMapping
    PagedModel<EventResponse> list(@PageableDefault(size = 20, sort = "startsAt") Pageable pageable) {
        pageable.getSort().forEach(order -> {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Unsupported sort property: " + order.getProperty() + ". Allowed: " + new java.util.TreeSet<>(SORTABLE));
            }
        });
        return new PagedModel<>(service.list(pageable).map(EventResponse::from));
    }

    @PutMapping("/{id}")
    EventResponse update(@PathVariable long id, @Valid @RequestBody EventRequest request, Principal principal) {
        return EventResponse.from(service.update(id, request, CurrentUser.id(principal)));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable long id, Principal principal) {
        service.delete(id, CurrentUser.id(principal));
        return ResponseEntity.noContent().build();
    }
}
