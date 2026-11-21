package com.example.eventsbooking.waitlist;

import jakarta.validation.constraints.NotNull;

public record WaitlistRequest(@NotNull WaitlistChannel channel) {
}
