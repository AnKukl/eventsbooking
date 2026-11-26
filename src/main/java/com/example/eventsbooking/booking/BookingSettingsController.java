package com.example.eventsbooking.booking;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The booking rules a client needs before it books: the seat limit of one booking, how long a hold lasts and until
 * when a cancellation is refunded. They come from the configuration, so the form never disagrees with the server.
 */
@RestController
class BookingSettingsController {

    record BookingSettings(int maxSeatsPerBooking, long holdDurationSeconds, long cancellationNoticeHours) {
    }

    private final BookingProperties properties;

    BookingSettingsController(BookingProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/api/settings/booking")
    BookingSettings settings() {
        return new BookingSettings(properties.maxSeatsPerBooking(), properties.holdDuration().toSeconds(),
                properties.cancellationNotice().toHours());
    }
}
