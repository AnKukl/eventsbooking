package com.example.eventsbooking.reminder;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Each reminder is one SQL statement that claims the events and creates the notifications together.
 *
 * <p>Claiming ({@code UPDATE ... WHERE reminder_..._sent_at IS NULL}) is what keeps several application instances (or two
 * overlapping runs) from sending twice: the second statement waits for the first one's row locks, then no longer sees an
 * unclaimed event. As a second line of defence every notification has a key derived from (type, user, event) and the
 * insert ignores a key that already exists.
 */
@Repository
class ReminderRepository {

    private static final String SEND = """
            WITH due AS (
                UPDATE events SET %1$s = :now
                WHERE %1$s IS NULL AND starts_at > CAST(:now AS timestamptz) + CAST(:from AS interval)
                  AND starts_at <= CAST(:now AS timestamptz) + CAST(:to AS interval)
                RETURNING id, title
            )
            INSERT INTO notifications (event_id, target_event_id, user_id, type, title, body, created_at)
            SELECT CAST(md5('%2$s:' || a.user_id || ':' || due.id) AS uuid), due.id, a.user_id, '%2$s',
                   '%3$s', 'Reminder: "' || due.title || '" %4$s.', :now
            FROM due
            JOIN (SELECT event_id, user_id FROM bookings WHERE status = 'ACTIVE'
                  UNION
                  SELECT event_id, user_id FROM favorites) a ON a.event_id = due.id
            ON CONFLICT (event_id) DO NOTHING
            """;

    private final JdbcClient jdbc;

    ReminderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Events starting in more than 2 and at most 24 hours; returns how many notifications were created. */
    int sendDayBefore(Instant now) {
        return send(SEND.formatted("reminder_24h_sent_at", "REMINDER_24H", "Event tomorrow", "starts in about a day"),
                now, "2 hours", "24 hours");
    }

    /** Events starting in the next 2 hours; returns how many notifications were created. */
    int sendTwoHoursBefore(Instant now) {
        return send(SEND.formatted("reminder_2h_sent_at", "REMINDER_2H", "Event soon", "starts in less than 2 hours"),
                now, "0 seconds", "2 hours");
    }

    private int send(String sql, Instant now, String from, String to) {
        return jdbc.sql(sql)
                .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .param("from", from)
                .param("to", to)
                .update();
    }
}
