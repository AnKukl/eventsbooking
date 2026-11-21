package com.example.eventsbooking;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The preview migration (V8) runs over a database that already holds events written by V7 and earlier. */
@Testcontainers
class EventPreviewMigrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Test
    void existingEventsGetSafePreviewValuesWithoutErrors() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).target("7").load().migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String insert = "insert into events (title, description, starts_at, capacity, organizer_id) "
                + "values (?, ?, now() + interval '1 day', 10, 1)";
        jdbc.update(insert, "long", "word ".repeat(400));                       // 2000 characters
        jdbc.update(insert, "none", null);                                      // no description at all
        jdbc.update(insert, "spaced", "  Hello\n\n   brave   world  ");        // messy whitespace
        jdbc.update(insert, "short", "Fits already");

        Flyway.configure().dataSource(dataSource).load().migrate();             // up to the latest version

        assertThat(jdbc.queryForObject("select success from flyway_schema_history where version = '8'", Boolean.class))
                .isTrue();
        assertThat(shortDescription(jdbc, "long")).hasSize(160).startsWith("word word");
        assertThat(shortDescription(jdbc, "none")).isEmpty();
        assertThat(shortDescription(jdbc, "spaced")).isEqualTo("Hello brave world");
        assertThat(shortDescription(jdbc, "short")).isEqualTo("Fits already");
        for (String table : List.of("event_audience_fit", "event_audience_not_fit", "event_media")) {
            assertThat(jdbc.queryForObject("select count(*) from " + table, Long.class)).as(table).isZero();
        }
    }

    private static String shortDescription(JdbcTemplate jdbc, String title) {
        return jdbc.queryForObject("select short_description from events where title = ?", String.class, title);
    }
}
