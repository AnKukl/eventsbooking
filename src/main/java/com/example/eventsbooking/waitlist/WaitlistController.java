package com.example.eventsbooking.waitlist;

import com.example.eventsbooking.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/events/{eventId}/waitlist")
class WaitlistController {

    private final WaitlistService service;

    WaitlistController(WaitlistService service) {
        this.service = service;
    }

    /** 201 when the user joined the queue, 200 when they were already in it. The user comes from the token. */
    @PostMapping
    ResponseEntity<Void> join(@PathVariable long eventId, @Valid @RequestBody WaitlistRequest request,
            Principal principal) {
        boolean created = service.join(CurrentUser.id(principal), eventId, request.channel());
        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK).build();
    }
}
