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

/** Events created before the cabinet existed stay public, with the waitlist on and nothing else changed. */
@Testcontainers
class EventStatusMigrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Test
    void existingEventsStayPublishedWithTheWaitlistOn() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).target("12").load().migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("insert into events (title, starts_at, capacity, organizer_id) "
                + "values ('Old show', now() + interval '10 days', 5, 1)");

        Flyway.configure().dataSource(dataSource).load().migrate();

        Map<String, Object> row = jdbc.queryForMap("select * from events where title = 'Old show'");
        assertThat(row.get("status")).isEqualTo("PUBLISHED");
        assertThat(row.get("waitlist_enabled")).isEqualTo(true);
        assertThat(row.get("revision")).isEqualTo(0L);
        assertThat(row.get("venue")).isNull();
        assertThat(row.get("price")).isNull();
    }
}
