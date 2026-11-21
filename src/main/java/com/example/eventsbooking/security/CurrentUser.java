package com.example.eventsbooking.security;

import org.springframework.security.authentication.BadCredentialsException;

import java.security.Principal;

/**
 * The authenticated user's id. It is the {@code sub} claim of the verified JWT (set by {@link JwtService} to the
 * user's id), never something the client sends in a request body.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static long id(Principal principal) {
        if (principal == null) {
            throw new BadCredentialsException("Not authenticated");
        }
        try {
            return Long.parseLong(principal.getName());
        } catch (NumberFormatException e) {
            throw new BadCredentialsException("Token subject is not a user id");
        }
    }

    /** For endpoints open to guests: null means "not logged in". A bad subject still fails. */
    public static Long idOrNull(Principal principal) {
        return principal == null ? null : id(principal);
    }
}
