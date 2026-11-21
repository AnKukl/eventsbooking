package com.example.eventsbooking.booking;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** The user always comes from the token. {@code seats} defaults to 1; the upper limit is checked by the server. */
public record BookingRequest(@NotNull @Positive Long eventId, @Min(1) Integer seats) {
}
