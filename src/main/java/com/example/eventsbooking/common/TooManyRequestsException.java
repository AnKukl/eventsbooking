package com.example.eventsbooking.common;

import java.time.Duration;

/** The caller is doing something too often; reported as 429 with a Retry-After header. */
public class TooManyRequestsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyRequestsException(Duration retryAfter) {
        super("Too many requests, retry in " + retryAfter.toSeconds() + " seconds");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
