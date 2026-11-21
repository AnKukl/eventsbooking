package com.example.eventsbooking.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update: {@code null} (field absent) keeps the stored value, an empty string clears name, phone and city.
 * There is deliberately no email here.
 */
public record ProfileUpdateRequest(
        @Size(max = 100) String name,
        @Pattern(regexp = "^$|^\\+[1-9]\\d{7,14}$",
                message = "must be a phone number in international format, e.g. +48123456789") String phone,
        @Pattern(regexp = "RU|PL|EN", message = "must be one of RU, PL, EN") String language,
        @Size(max = 100) String city) {
}
