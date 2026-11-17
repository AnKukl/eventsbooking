package com.example.eventsbooking.booking;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** @param cancellationNotice minimum time between the cancellation and the event start (default 24h) */
@ConfigurationProperties("app.booking")
public record BookingProperties(@DefaultValue("24h") Duration cancellationNotice) {
}
