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

    record UnreadCount(long count) {
    }

    /** {@code ids}: which ones; absent or empty: all of them. */
    record MarkReadRequest(@jakarta.validation.constraints.Size(max = 500) java.util.List<Long> ids) {
    }

    @GetMapping("/unread-count")
    UnreadCount unreadCount(Principal principal) {
        return new UnreadCount(service.unreadCount(CurrentUser.id(principal)));
    }

    /** Opening the feed marks it read. The user's own notifications only; repeating it is harmless. */
    @org.springframework.web.bind.annotation.PostMapping("/read")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody(required = false) MarkReadRequest request,
            Principal principal) {
        service.markRead(CurrentUser.id(principal), request == null ? null : request.ids());
    }
}
