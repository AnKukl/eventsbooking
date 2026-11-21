package com.example.eventsbooking;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Photos uploaded before the event_images table existed must not look like orphans to the cleanup. */
@Testcontainers
class EventImagesMigrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Test
    void existingUploadedPhotosBecomeAttachedRowsAndExternalUrlsAreIgnored() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).target("15").load().migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("insert into events (title, starts_at, capacity, organizer_id) "
                + "values ('Old show', now() + interval '10 days', 5, 1)");
        long eventId = jdbc.queryForObject("select max(id) from events", Long.class);
        jdbc.update("insert into event_media (event_id, position, url) values (?, 0, "
                + "'/api/media/0d9f2f5e-7c63-4c1f-9a60-0a3f7d1c7c11.png')", eventId);
        jdbc.update("insert into event_media (event_id, position, url) values (?, 1, 'https://cdn.example.com/a.png')",
                eventId);

        Flyway.configure().dataSource(dataSource).load().migrate();

        List<Map<String, Object>> rows = jdbc.queryForList("select * from event_images");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("storage_key")).isEqualTo("0d9f2f5e-7c63-4c1f-9a60-0a3f7d1c7c11.png");
        assertThat(rows.get(0).get("status")).isEqualTo("ATTACHED");
        assertThat(((Number) rows.get(0).get("event_id")).longValue()).isEqualTo(eventId);
    }
}
