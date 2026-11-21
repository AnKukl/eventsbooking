package com.example.eventsbooking.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmEmailRequest(@NotBlank @Size(max = 200) String token) {

    /** The token is a credential: it must not end up in a log line through the request object. */
    @Override
    public String toString() {
        return "ConfirmEmailRequest[token=***]";
    }
}
