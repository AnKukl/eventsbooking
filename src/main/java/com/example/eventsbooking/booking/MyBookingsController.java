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

    MyBookingsController(BookingService service) {
        this.service = service;
    }

    @GetMapping
    PagedModel<BookingResponse> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Principal principal) {
        pageable.getSort().forEach(order -> {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unsupported sort property: " + order.getProperty() + ". Allowed: " + new TreeSet<>(SORTABLE));
            }
        });
        return new PagedModel<>(service.findMine(CurrentUser.id(principal), pageable).map(BookingResponse::from));
    }
}
