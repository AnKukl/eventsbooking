package com.example.eventsbooking.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Only http(s) URLs: other schemes (javascript:, data:) must never reach a client that renders them. */
public record MediaRequest(
        @NotBlank @Size(max = 2048) @Pattern(regexp = "https?://\\S+", message = "must be an http(s) URL") String url,
        @Size(max = 200) String alt) {
}
