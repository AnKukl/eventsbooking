package com.example.eventsbooking.user;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

/**
 * @param role optional, defaults to USER. Self-service ORGANIZER registration is a known simplification
 *             (see docs/decisions.md).
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        Role role) {

    /** BCrypt only uses the first 72 bytes and Spring rejects longer input, so check bytes, not characters. */
    @AssertTrue(message = "password must be at most 72 bytes in UTF-8")
    public boolean isPasswordWithin72Bytes() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", role=" + role + "]";
    }
}
