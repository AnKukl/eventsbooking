package com.example.eventsbooking.user;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public on purpose: the link may be opened in another browser; possession of the token is the credential. */
@RestController
@RequestMapping("/api/auth/confirm-email")
class EmailConfirmationController {

    private final ProfileService service;

    EmailConfirmationController(ProfileService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<Void> confirm(@Valid @RequestBody ConfirmEmailRequest request) {
        service.confirmEmail(request.token());
        return ResponseEntity.noContent().build();
    }
}
