package com.example.eventsbooking.organizer;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Partial update: a field that is absent stays as it is, an empty list or string clears. {@code version} is required
 * and must be the one the organizer loaded; an older one is refused with 409.
 */
public record OrganizerEventUpdateRequest(
        @NotNull Long version,
        @Size(max = 200) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String title,
        @Size(max = 2000) String description,
        Instant startsAt,
        @Positive Integer capacity,
        @Size(max = 160) String shortDescription,
        @Size(max = 200) String venue,
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
        Boolean waitlistEnabled,
        @Size(max = 4) List<@jakarta.validation.constraints.NotBlank @Size(max = 100) String> audienceFit,
        @Size(max = 3) List<@jakarta.validation.constraints.NotBlank @Size(max = 100) String> audienceNotFit,
        @jakarta.validation.constraints.DecimalMin("-90") @jakarta.validation.constraints.DecimalMax("90") BigDecimal latitude,
        @jakarta.validation.constraints.DecimalMin("-180") @jakarta.validation.constraints.DecimalMax("180") BigDecimal longitude) {

    /** A point on the map needs both coordinates (reported as the field "location"). */
    @jakarta.validation.constraints.AssertTrue(message = "latitude and longitude go together")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isLocation() {
        return (latitude == null) == (longitude == null);
    }
}
