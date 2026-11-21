package com.example.eventsbooking.booking;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param cancellationNotice  how long before the start a confirmed booking can still be cancelled with a refund
 *                            (default 24h; later cancellations are allowed but not refunded)
 * @param maxSeatsPerBooking  upper limit of seats in one booking, enforced by the server (default 4)
 * @param holdDuration        how long a HOLD keeps its seats (default 10m)
 */
@ConfigurationProperties("app.booking")
public record BookingProperties(
        @DefaultValue("24h") Duration cancellationNotice,
        @DefaultValue("4") int maxSeatsPerBooking,
        @DefaultValue("10m") Duration holdDuration) {
}
