package com.example.eventsbooking.storage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true"
})
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class EventImageRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    private final EventImageRepository images;
    private final JdbcTemplate jdbc;

    EventImageRepositoryIntegrationTest(EventImageRepository images, JdbcTemplate jdbc) {
        this.images = images;
        this.jdbc = jdbc;
    }

    @Test
    void aNewRowIsPendingAndHasNoEvent() {
        images.insertPending("one.png", NOW);

        Map<String, Object> row = jdbc.queryForMap("select * from event_images where storage_key = 'one.png'");
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("event_id")).isNull();
        assertThat(((Number) row.get("version")).longValue()).isZero();
    }

    @Test
    void attachingMakesTheRowsAttachedToTheEventAndBumpsTheVersion() {
        images.insertPending("two.png", NOW);
        images.insertPending("three.png", NOW);

        jdbc.update("insert into events (title, starts_at, capacity, organizer_id) "
                + "values ('Show', now() + interval '10 days', 5, 1)");
        long eventId = jdbc.queryForObject("select max(id) from events", Long.class);

        images.attach(eventId, List.of("two.png"));

        Map<String, Object> attached = jdbc.queryForMap("select * from event_images where storage_key = 'two.png'");
        assertThat(attached.get("status")).isEqualTo("ATTACHED");
        assertThat(((Number) attached.get("event_id")).longValue()).isEqualTo(eventId);
        assertThat(((Number) attached.get("version")).longValue()).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select status from event_images where storage_key = 'three.png'", String.class))
                .isEqualTo("PENDING");
    }

    @Test
    void aStorageKeyIsUnique() {
        images.insertPending("dup.png", NOW);

        assertThatThrownBy(() -> images.insertPending("dup.png", NOW)).isInstanceOf(DuplicateKeyException.class);
    }
}
