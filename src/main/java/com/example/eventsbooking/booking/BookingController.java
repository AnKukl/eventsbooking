package com.example.eventsbooking.booking;

import com.example.eventsbooking.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/** The booking always belongs to the authenticated user; the client cannot book, confirm or cancel for someone else. */
@RestController
@RequestMapping("/api/bookings")
class BookingController {

    private final BookingService service;

    BookingController(BookingService service) {
        this.service = service;
    }

    /**
     * Holds seats. With an {@code Idempotency-Key} a repeated request returns the same booking: 201 the first time,
     * 200 (and {@code Idempotent-Replayed: true}) afterwards.
     */
    @PostMapping
    ResponseEntity<BookingResponse> book(@Valid @RequestBody BookingRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Size(min = 1, max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+") String idempotencyKey,
            Principal principal) {
        int seats = request.seats() == null ? 1 : request.seats();
        BookingOutcome outcome = service.book(request.eventId(), CurrentUser.id(principal), seats, idempotencyKey);
        if (outcome.created()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(BookingResponse.from(outcome.booking()));
        }
        return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(BookingResponse.from(outcome.booking()));
    }

    /** HOLD to CONFIRMED; until the payment stage exists, this is what a successful payment will trigger. */
    @PostMapping("/{id}/confirm")
    BookingResponse confirm(@PathVariable long id, Principal principal) {
        return BookingResponse.from(service.confirm(id, CurrentUser.id(principal)));
    }

    @PostMapping("/{id}/cancel")
    BookingResponse cancel(@PathVariable long id, @Valid @RequestBody CancelBookingRequest request,
            Principal principal) {
        return BookingResponse.from(service.cancel(id, CurrentUser.id(principal), request.reason()));
    }
}
