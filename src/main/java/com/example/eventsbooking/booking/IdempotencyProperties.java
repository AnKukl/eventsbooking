package com.example.eventsbooking.booking;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param ttl how long a key is remembered. It must stay longer than any realistic window in which a client retries
 *            the same request, otherwise a late retry would create a second booking.
 */
@ConfigurationProperties("app.idempotency")
public record IdempotencyProperties(@DefaultValue("PT24H") Duration ttl) {
}
