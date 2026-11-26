package com.example.eventsbooking.user;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

/**
 * @param inviteCode optional. A correct organizer invite code makes the new account an ORGANIZER; without it the
 *                   account is always a plain USER. The role itself cannot be chosen by the client.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Size(max = 200) String inviteCode) {

    /** BCrypt only uses the first 72 bytes and Spring rejects longer input, so check bytes, not characters. */
    @AssertTrue(message = "password must be at most 72 bytes in UTF-8")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isPasswordWithin72Bytes() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    /** Neither the password nor the invite code may end up in logs. */
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + "]";
    }
}
