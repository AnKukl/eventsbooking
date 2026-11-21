package com.example.eventsbooking.organizer;

import com.example.eventsbooking.event.MediaResponse;
import com.example.eventsbooking.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** The organizer cabinet. The caller is always the authenticated user; the service checks ownership of every event. */
@RestController
@RequestMapping("/api/organizer/events")
class OrganizerEventController {

    private static final Set<String> SORTABLE = Set.of("id", "title", "startsAt", "capacity");

    private final OrganizerEventService events;
    private final EventMediaService media;
    private final OrganizerBookingsService bookings;

    OrganizerEventController(OrganizerEventService events, EventMediaService media, OrganizerBookingsService bookings) {
        this.events = events;
        this.media = media;
        this.bookings = bookings;
    }

    @GetMapping
    PagedModel<OrganizerEventResponse> list(@RequestParam(required = false) OrganizerEventStatus status,
            @PageableDefault(size = 20, sort = "startsAt", direction = Sort.Direction.DESC) Pageable pageable,
            Principal principal) {
        pageable.getSort().forEach(order -> {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unsupported sort property: " + order.getProperty() + ". Allowed: " + new TreeSet<>(SORTABLE));
            }
        });
        return new PagedModel<>(events.list(CurrentUser.id(principal), status, pageable));
    }

    @GetMapping("/{id}")
    OrganizerEventResponse get(@PathVariable long id, Principal principal) {
        return events.get(id, CurrentUser.id(principal));
    }

    @PostMapping
    ResponseEntity<OrganizerEventResponse> create(@Valid @RequestBody OrganizerEventCreateRequest request,
            Principal principal) {
        OrganizerEventResponse created = events.create(request, CurrentUser.id(principal));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PatchMapping("/{id}")
    OrganizerEventResponse update(@PathVariable long id, @Valid @RequestBody OrganizerEventUpdateRequest request,
            Principal principal) {
        return events.update(id, request, CurrentUser.id(principal));
    }

    /** 200 when published (or already published), 422 with the list of unfilled fields otherwise. */
    @PutMapping("/{id}/publish")
    OrganizerEventResponse publish(@PathVariable long id, Principal principal) {
        return events.publish(id, CurrentUser.id(principal));
    }

    /** Up to 6 photos per event (JPG, PNG or WebP, 8 MB each); the file part is named {@code files}. */
    @PostMapping(path = "/{id}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<List<MediaResponse>> uploadMedia(@PathVariable long id,
            @RequestParam("files") List<MultipartFile> files, Principal principal) {
        List<UploadedImage> images = files.stream().map(OrganizerEventController::toImage).toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(media.upload(id, CurrentUser.id(principal), images));
    }

    @GetMapping("/{id}/bookings")
    PagedModel<OrganizerBookingResponse> bookings(@PathVariable long id,
            @PageableDefault(size = 20) Pageable pageable, Principal principal) {
        if (pageable.getSort().isSorted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Sorting is not supported: bookings are listed newest first");
        }
        return new PagedModel<>(bookings.list(id, CurrentUser.id(principal), pageable));
    }

    private static UploadedImage toImage(MultipartFile file) {
        try {
            return new UploadedImage(file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }
    }
}
