package com.example.eventsbooking.booking;

import jakarta.validation.constraints.NotNull;

public record BookingRequest(@NotNull Long userId) {
}
