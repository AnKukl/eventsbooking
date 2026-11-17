package com.example.eventsbooking.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param secret HMAC key, taken from the JWT_SECRET environment variable (at least 32 bytes)
 * @param issuer value of the {@code iss} claim
 * @param ttl    token lifetime
 */
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("eventsbooking") String issuer,
        @DefaultValue("1h") Duration ttl) {

    /** The secret must never end up in logs, so it is left out of the string form. */
    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", ttl=" + ttl + ", secret=***]";
    }
}
