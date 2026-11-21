package com.example.eventsbooking.organizer;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Every number is computed by the database in one statement; nothing is summed in application memory. */
@Repository
public class AnalyticsRepository {

    /**
     * @param sold           seats of confirmed bookings made inside the range
     * @param revenue        those seats times the price of their event (an event without a price counts as free)
     * @param capacity       total capacity of the organizer's published events
     * @param confirmedSeats seats of confirmed bookings on those events, whenever they were made
     */
    public record Aggregates(long sold, BigDecimal revenue, long capacity, long confirmedSeats) {
    }

    private final JdbcClient jdbc;

    AnalyticsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Aggregates aggregate(long organizerId, Instant from, Instant to) {
        return jdbc.sql("""
                SELECT
                  (SELECT COALESCE(SUM(b.seats), 0)
                     FROM bookings b JOIN events e ON e.id = b.event_id
                    WHERE e.organizer_id = :organizerId AND b.status = 'CONFIRMED'
                      AND b.created_at >= :from AND b.created_at < :to)                          AS sold,
                  (SELECT COALESCE(SUM(b.seats * COALESCE(e.price, 0)), 0)
                     FROM bookings b JOIN events e ON e.id = b.event_id
                    WHERE e.organizer_id = :organizerId AND b.status = 'CONFIRMED'
                      AND b.created_at >= :from AND b.created_at < :to)                          AS revenue,
                  (SELECT COALESCE(SUM(e.capacity), 0)
                     FROM events e WHERE e.organizer_id = :organizerId AND e.status = 'PUBLISHED') AS capacity,
                  (SELECT COALESCE(SUM(b.seats), 0)
                     FROM bookings b JOIN events e ON e.id = b.event_id
                    WHERE e.organizer_id = :organizerId AND b.status = 'CONFIRMED'
                      AND e.status = 'PUBLISHED')                                                AS confirmed_seats
                """)
                .param("organizerId", organizerId)
                .param("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
                .param("to", OffsetDateTime.ofInstant(to, ZoneOffset.UTC))
                .query((rs, row) -> new Aggregates(rs.getLong("sold"), rs.getBigDecimal("revenue"),
                        rs.getLong("capacity"), rs.getLong("confirmed_seats")))
                .single();
    }
}
