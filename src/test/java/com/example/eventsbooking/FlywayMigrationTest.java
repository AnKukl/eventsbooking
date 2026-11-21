package com.example.eventsbooking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.flyway.enabled=true")
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FlywayMigrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    private final JdbcTemplate jdbc;

    FlywayMigrationTest(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Test
    void v1MigrationIsAppliedSuccessfully() {
        Boolean success = jdbc.queryForObject(
                "select success from flyway_schema_history where version = '1'", Boolean.class);

        assertThat(success).isTrue();
    }

    @Test
    void usersTableHasExpectedColumns() {
        List<String> columns = jdbc.queryForList(
                "select column_name from information_schema.columns where table_name = 'users'",
                String.class);

        assertThat(columns).containsExactlyInAnyOrder(
                "id", "email", "password_hash", "role", "created_at");
    }

    @Test
    void favoritesPairIsUniqueAndTheCascadeFollowsTheEvent() {
        jdbc.update("insert into events (title, starts_at, capacity, organizer_id) values ('e', now(), 5, 1)");
        Long eventId = jdbc.queryForObject("select max(id) from events", Long.class);
        String insert = "insert into favorites (user_id, event_id, created_at) values (?, ?, now())";
        jdbc.update(insert, 1L, eventId);

        assertThatThrownBy(() -> jdbc.update(insert, 1L, eventId))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(insert, 2L, eventId); // another user may favorite the same event

        jdbc.update("delete from events where id = ?", eventId);
        assertThat(jdbc.queryForObject("select count(*) from favorites where event_id = ?", Long.class, eventId))
                .isZero();
    }

    @Test
    void favoritesHasAnIndexOnUserAndCreatedAt() {
        List<String> indexes = jdbc.queryForList(
                "select indexdef from pg_indexes where tablename = 'favorites'", String.class);

        assertThat(indexes).anyMatch(def -> def.contains("(user_id, created_at"));
    }

    @Test
    void emailMustBeUnique() {
        String insert = "insert into users (email, password_hash, role) values (?, ?, ?)";
        jdbc.update(insert, "a@example.com", "hash", "USER");

        assertThatThrownBy(() -> jdbc.update(insert, "a@example.com", "hash2", "USER"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
