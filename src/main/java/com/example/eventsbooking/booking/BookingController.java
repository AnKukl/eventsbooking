package com.example.eventsbooking.booking;

import com.example.eventsbooking.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/** The booking always belongs to the authenticated user; the client cannot book or cancel for someone else. */
@RestController
@RequestMapping("/api/events/{eventId}/bookings")
class BookingController {

    private final BookingService service;

    BookingController(BookingService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    BookingResponse book(@PathVariable long eventId, Principal principal) {
        return BookingResponse.from(service.book(eventId, CurrentUser.id(principal)));
    }

    @PostMapping("/{bookingId}/cancel")
    BookingResponse cancel(@PathVariable long eventId, @PathVariable long bookingId, Principal principal) {
        return BookingResponse.from(service.cancel(eventId, bookingId, CurrentUser.id(principal)));
    }
}
