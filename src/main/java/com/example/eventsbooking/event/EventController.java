package com.example.eventsbooking.event;

import jakarta.validation.Valid;
import java.security.Principal;
import com.example.eventsbooking.favorite.FavoriteService;
import com.example.eventsbooking.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.CacheControl;
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
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/events")
class EventController {

    private final EventService service;
    private final FavoriteService favorites;
    private final EventCovers covers;

    EventController(EventService service, FavoriteService favorites, EventCovers covers) {
        this.service = service;
        this.favorites = favorites;
        this.covers = covers;
    }

    @PostMapping
    ResponseEntity<EventResponse> create(@Valid @RequestBody EventRequest request, Principal principal) {
        Event created = service.create(request, CurrentUser.id(principal));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(EventResponse.from(created, false, coverOf(created.getId())));
    }

    @GetMapping("/{id}")
    EventResponse get(@PathVariable long id, Principal principal) {
        Event event = service.get(id);
        boolean favorite = favorites.favoriteEventIds(CurrentUser.idOrNull(principal), List.of(id)).contains(id);
        return EventResponse.from(event, favorite, coverOf(id));
    }

    /**
     * Preview card data. Identical for every viewer, so it is cacheable by anyone for five minutes; the content ETag
     * lets a client revalidate cheaply (304). Spring answers a matching If-None-Match with 304 and keeps the headers.
     */
    @GetMapping("/{id}/preview")
    ResponseEntity<EventPreviewResponse> preview(@PathVariable long id) {
        EventPreviewResponse preview = service.preview(id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .eTag(preview.etag())
                .body(preview);
    }

    /** Only public, indexed columns may be used for sorting; internal fields (version, organizerId, ...) may not. */
    private static final java.util.Set<String> SORTABLE = java.util.Set.of("id", "title", "startsAt", "capacity");

    @GetMapping
    PagedModel<EventResponse> list(@PageableDefault(size = 20, sort = "startsAt") Pageable pageable,
            Principal principal) {
        pageable.getSort().forEach(order -> {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Unsupported sort property: " + order.getProperty() + ". Allowed: " + new java.util.TreeSet<>(SORTABLE));
            }
        });
        Page<Event> page = service.list(pageable);
        // one lookup for the whole page, not one per event
        List<Long> ids = page.getContent().stream().map(Event::getId).toList();
        Set<Long> favored = favorites.favoriteEventIds(CurrentUser.idOrNull(principal), ids);
        java.util.Map<Long, String> coverUrls = covers.coverUrls(ids);
        return new PagedModel<>(page.map(event ->
                EventResponse.from(event, favored.contains(event.getId()), coverUrls.get(event.getId()))));
    }

    @PutMapping("/{id}")
    EventResponse update(@PathVariable long id, @Valid @RequestBody EventRequest request, Principal principal) {
        long userId = CurrentUser.id(principal);
        Event event = service.update(id, request, userId);
        return EventResponse.from(event, favorites.favoriteEventIds(userId, List.of(id)).contains(id),
                coverOf(id));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable long id, Principal principal) {
        service.delete(id, CurrentUser.id(principal));
        return ResponseEntity.noContent().build();
    }

    private String coverOf(Long eventId) {
        return eventId == null ? null : covers.coverUrls(List.of(eventId)).get(eventId);
    }
}
