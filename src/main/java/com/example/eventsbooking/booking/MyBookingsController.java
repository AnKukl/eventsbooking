package com.example.eventsbooking.booking;

import com.example.eventsbooking.security.CurrentUser;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Set;
import java.util.TreeSet;

/** The authenticated user's own bookings (cancelled ones included, newest first). */
@RestController
@RequestMapping("/api/bookings/my")
class MyBookingsController {

    private static final Set<String> SORTABLE = Set.of("id", "createdAt", "status");

    private final BookingService service;
    private final com.example.eventsbooking.event.EventRepository events;

    MyBookingsController(BookingService service, com.example.eventsbooking.event.EventRepository events) {
        this.service = service;
        this.events = events;
    }

    @GetMapping
    PagedModel<MyBookingResponse> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Principal principal) {
        pageable.getSort().forEach(order -> {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unsupported sort property: " + order.getProperty() + ". Allowed: " + new TreeSet<>(SORTABLE));
            }
        });
        var page = service.findMine(CurrentUser.id(principal), pageable);
        // one query for the events of the whole page
        java.util.Map<Long, com.example.eventsbooking.event.Event> byId = new java.util.HashMap<>();
        events.findAllById(page.getContent().stream().map(Booking::getEventId).distinct().toList())
                .forEach(event -> byId.put(event.getId(), event));
        return new PagedModel<>(page.map(booking -> MyBookingResponse.from(booking, byId.get(booking.getEventId()))));
    }
}
