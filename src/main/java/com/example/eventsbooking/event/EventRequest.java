package com.example.eventsbooking.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * The preview fields are optional: {@code null} leaves what is stored (an empty preview for a new event), an
 * empty list or empty string clears it, anything else replaces it.
 */
public record EventRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @NotNull @Future Instant startsAt,
        @NotNull @Positive Integer capacity,
        @Size(max = 160) String shortDescription,
        @Size(max = 4) List<@NotBlank @Size(max = 100) String> audienceFit,
        @Size(max = 3) List<@NotBlank @Size(max = 100) String> audienceNotFit,
        @Size(max = 6) List<@Valid @NotNull MediaRequest> media) {

    /** An event without preview fields. */
    public EventRequest(String title, String description, Instant startsAt, Integer capacity) {
        this(title, description, startsAt, capacity, null, null, null, null);
    }
}
