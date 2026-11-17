package com.example.eventsbooking.security;

/** A freshly issued access token. The value is a credential: do not log it. */
public record IssuedToken(String value, long expiresInSeconds) {

    @Override
    public String toString() {
        return "IssuedToken[expiresInSeconds=" + expiresInSeconds + "]";
    }
}
