package com.example.eventsbooking;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The notifications table already holds booking confirmations from the Kafka consumer when it becomes a feed. */
@Testcontainers
class NotificationsFeedMigrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Test
    void existingBookingNotificationsBecomeFeedItems() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).target("9").load().migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("insert into events (title, starts_at, capacity, organizer_id) "
                + "values ('Old show', now() + interval '10 days', 5, 1)");
        long eventId = jdbc.queryForObject("select max(id) from events", Long.class);
        jdbc.update("insert into bookings (event_id, user_id) values (?, 42)", eventId);
        long bookingId = jdbc.queryForObject("select max(id) from bookings", Long.class);
        jdbc.update("insert into notifications (event_id, booking_id, user_id, message) "
                + "values (gen_random_uuid(), ?, 42, 'Booking confirmed: user 42 booked event')", bookingId);

        Flyway.configure().dataSource(dataSource).load().migrate();

        Map<String, Object> row = jdbc.queryForMap("select * from notifications where booking_id = ?", bookingId);
        assertThat(row.get("type")).isEqualTo("BOOKING_CONFIRMED");
        assertThat(row.get("title")).isEqualTo("Booking confirmed");
        assertThat(row.get("body")).isEqualTo("Booking confirmed: user 42 booked event");
        assertThat(row.get("target_event_id")).isEqualTo(eventId);
        assertThat(row.get("read_at")).isNull();
        assertThat(row.get("user_id")).isEqualTo(42L);
        assertThat(row).doesNotContainKey("message");
    }
}
