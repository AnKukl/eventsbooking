package com.example.eventsbooking.security;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

/**
 * The session cookies. The access token goes to every path ({@code SameSite=Lax}); the refresh token only to
 * {@code /api/auth} ({@code SameSite=Strict}). Both are HttpOnly: a script on the page cannot read them.
 */
public class AuthCookies {

    public static final String ACCESS = "access_token";
    public static final String REFRESH = "refresh_token";
    static final String REFRESH_PATH = "/api/auth";

    private final CookieProperties properties;

    public AuthCookies(CookieProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie access(String token, Duration maxAge) {
        return cookie(ACCESS, token, "/", "Lax", maxAge);
    }

    public ResponseCookie refresh(String token, Duration maxAge) {
        return cookie(REFRESH, token, REFRESH_PATH, "Strict", maxAge);
    }

    public ResponseCookie clearAccess() {
        return cookie(ACCESS, "", "/", "Lax", Duration.ZERO);
    }

    public ResponseCookie clearRefresh() {
        return cookie(REFRESH, "", REFRESH_PATH, "Strict", Duration.ZERO);
    }

    private ResponseCookie cookie(String name, String value, String path, String sameSite, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite(sameSite)
                .path(path)
                .maxAge(maxAge)
                .build();
    }
}
