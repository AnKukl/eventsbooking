package com.example.eventsbooking.user;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Limit for login, registration and refresh: {@code attempts} per {@code period}, separately per client address and
 * per email. Counted in memory: with several instances every instance has its own counters.
 */
@ConfigurationProperties("app.security.rate-limit")
public record RateLimitProperties(@DefaultValue("10") int attempts, @DefaultValue("PT1M") Duration period) {
}
