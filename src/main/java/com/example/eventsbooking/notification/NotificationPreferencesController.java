package com.example.eventsbooking.notification;

import com.example.eventsbooking.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/** The authenticated user's notification channel choices. The user always comes from the token. */
@RestController
@RequestMapping("/api/me/notifications/preferences")
class NotificationPreferencesController {

    private final NotificationPreferenceService service;

    NotificationPreferencesController(NotificationPreferenceService service) {
        this.service = service;
    }

    @GetMapping
    NotificationPreferencesResponse get(Principal principal) {
        return new NotificationPreferencesResponse(service.get(CurrentUser.id(principal)));
    }

    @PutMapping
    NotificationPreferencesResponse put(@Valid @RequestBody NotificationPreferencesRequest request,
            Principal principal) {
        return new NotificationPreferencesResponse(service.update(CurrentUser.id(principal),
                request.preferences().stream().map(NotificationPreferencesRequest.Item::toPreference).toList()));
    }
}
