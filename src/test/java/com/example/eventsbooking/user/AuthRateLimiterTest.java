package com.example.eventsbooking.user;

import com.example.eventsbooking.common.TooManyRequestsException;
import com.example.eventsbooking.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2030-01-01T10:00:00Z"));
    private final AuthRateLimiter limiter =
            new AuthRateLimiter(new RateLimitProperties(10, Duration.ofMinutes(1)), clock);

    @Test
    void theEleventhAttemptInAMinuteFromOneAddressIsRefusedWithAWaitTime() {
        for (int i = 0; i < 10; i++) {
            limiter.check("login", "10.0.0.1", "user" + i + "@example.com");
        }

        assertThatThrownBy(() -> limiter.check("login", "10.0.0.1", "other@example.com"))
                .isInstanceOfSatisfying(TooManyRequestsException.class, e ->
                        assertThat(e.getRetryAfter()).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(1)));
    }

    @Test
    void oneEmailIsLimitedAcrossAddresses() {
        for (int i = 0; i < 10; i++) {
            limiter.check("login", "10.0.1." + i, "victim@example.com");
        }

        assertThatThrownBy(() -> limiter.check("login", "10.0.2.1", "Victim@Example.com "))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void theLimitIsFreeAgainAfterTheMinute() {
        for (int i = 0; i < 10; i++) {
            limiter.check("login", "10.0.0.2", null);
        }
        clock.advance(Duration.ofMinutes(1));

        assertThatCode(() -> limiter.check("login", "10.0.0.2", null)).doesNotThrowAnyException();
    }

    @Test
    void eachEndpointHasItsOwnLimit() {
        for (int i = 0; i < 10; i++) {
            limiter.check("login", "10.0.0.3", null);
        }

        assertThatCode(() -> limiter.check("refresh", "10.0.0.3", null)).doesNotThrowAnyException();
    }
}
