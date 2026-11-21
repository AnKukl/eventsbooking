package com.example.eventsbooking.notification;

import com.example.eventsbooking.security.CurrentUser;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

/** The authenticated user's notification feed. The user always comes from the token. */
@RestController
@RequestMapping("/api/me/notifications")
class NotificationController {

    private final NotificationService service;

    NotificationController(NotificationService service) {
        this.service = service;
    }

    /** Newest first; the order is fixed, so {@code sort} is rejected. */
    @GetMapping
    PagedModel<NotificationResponse> feed(@PageableDefault(size = 20) Pageable pageable, Principal principal) {
        if (pageable.getSort().isSorted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Sorting is not supported: notifications are ordered by creation time, newest first");
        }
        return new PagedModel<>(service.feed(CurrentUser.id(principal), pageable).map(NotificationResponse::from));
    }
}
