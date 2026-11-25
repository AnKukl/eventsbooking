package com.example.eventsbooking.favorite;

import com.example.eventsbooking.event.EventResponse;
import com.example.eventsbooking.security.CurrentUser;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

/** The authenticated user's favorite events. The user always comes from the token. */
@RestController
@RequestMapping("/api/favorites")
class FavoriteController {

    private final FavoriteService service;
    private final com.example.eventsbooking.event.EventCovers covers;

    FavoriteController(FavoriteService service, com.example.eventsbooking.event.EventCovers covers) {
        this.covers = covers;
        this.service = service;
    }

    /** 201 when created, 200 when it was already a favorite. */
    @PostMapping("/{eventId}")
    ResponseEntity<Void> add(@PathVariable long eventId, Principal principal) {
        boolean created = service.add(CurrentUser.id(principal), eventId);
        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK).build();
    }

    /** Always 204, also when there was nothing to remove. */
    @DeleteMapping("/{eventId}")
    ResponseEntity<Void> remove(@PathVariable long eventId, Principal principal) {
        service.remove(CurrentUser.id(principal), eventId);
        return ResponseEntity.noContent().build();
    }

    /** Newest favorite first; the order is fixed, so {@code sort} is rejected. */
    @GetMapping
    PagedModel<EventResponse> list(@PageableDefault(size = 20) Pageable pageable, Principal principal) {
        if (pageable.getSort().isSorted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Sorting is not supported: favorites are ordered by the time they were added, newest first");
        }
        var page = service.list(CurrentUser.id(principal), pageable);
        var coverUrls = covers.coverUrls(page.getContent().stream().map(event -> event.getId()).toList());
        return new PagedModel<>(page.map(event -> EventResponse.from(event, true, coverUrls.get(event.getId()))));
    }
}
