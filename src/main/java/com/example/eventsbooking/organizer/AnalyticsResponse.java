package com.example.eventsbooking.organizer;

import java.math.BigDecimal;
import java.time.Instant;

/** @param occupancy confirmed seats divided by capacity over the organizer's published events (0 to 1) */
public record AnalyticsResponse(String range, Instant from, Instant to, long sold, BigDecimal revenue,
        BigDecimal occupancy) {
}
