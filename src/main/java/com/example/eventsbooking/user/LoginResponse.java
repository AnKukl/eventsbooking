package com.example.eventsbooking.user;

/** The token itself travels only in an HttpOnly cookie; the client just learns when it expires. */
public record LoginResponse(long expiresIn) {
}
