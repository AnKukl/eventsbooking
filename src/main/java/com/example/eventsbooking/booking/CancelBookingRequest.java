package com.example.eventsbooking.booking;

import jakarta.validation.constraints.NotNull;

public record CancelBookingRequest(@NotNull CancellationReason reason) {
}
