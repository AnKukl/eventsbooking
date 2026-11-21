package com.example.eventsbooking.organizer;

import com.example.eventsbooking.security.CurrentUser;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/organizer/analytics")
class OrganizerAnalyticsController {

    private final OrganizerAnalyticsService service;

    OrganizerAnalyticsController(OrganizerAnalyticsService service) {
        this.service = service;
    }

    @GetMapping
    AnalyticsResponse analytics(
            @RequestParam(defaultValue = "7d") @Pattern(regexp = "[1-9][0-9]{0,2}d", message = "must be a number of days followed by d, e.g. 7d") String range,
            Principal principal) {
        return service.analytics(CurrentUser.id(principal), range);
    }
}
