package com.example.eventsbooking.organizer;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A draft needs only what the database cannot do without (title, start, capacity). Venue, price and photos are checked
 * when the event is published, not here.
 */
public record OrganizerEventCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @NotNull Instant startsAt,
        @NotNull @Positive Integer capacity,
        @Size(max = 160) String shortDescription,
        @Size(max = 200) String venue,
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
        Boolean waitlistEnabled,
        @Size(max = 4) List<@NotBlank @Size(max = 100) String> audienceFit,
        @Size(max = 3) List<@NotBlank @Size(max = 100) String> audienceNotFit,
        @jakarta.validation.constraints.DecimalMin("-90") @jakarta.validation.constraints.DecimalMax("90") BigDecimal latitude,
        @jakarta.validation.constraints.DecimalMin("-180") @jakarta.validation.constraints.DecimalMax("180") BigDecimal longitude) {

    /** A point on the map needs both coordinates (reported as the field "location"). */
    @jakarta.validation.constraints.AssertTrue(message = "latitude and longitude go together")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isLocation() {
        return (latitude == null) == (longitude == null);
    }
}
