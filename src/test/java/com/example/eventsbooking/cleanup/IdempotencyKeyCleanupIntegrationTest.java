package com.example.eventsbooking.cleanup;

import com.example.eventsbooking.booking.IdempotencyKeys;
import com.example.eventsbooking.support.MutableClock;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Removal of expired idempotency keys against a real PostgreSQL, with a clock the test controls. */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true",
        "app.cleanup.idempotency.batch-size=2",
        "app.cleanup.idempotency.pause=PT0S",
        "app.idempotency.ttl=PT24H"
})
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class IdempotencyKeyCleanupIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(NOW);
        }
    }

    private final IdempotencyKeyCleanup cleanup;
    private final IdempotencyKeys keys;
    private final JdbcTemplate jdbc;
    private final MutableClock clock;
    private final MeterRegistry meters;
    private final ClusterLock lock;

    IdempotencyKeyCleanupIntegrationTest(IdempotencyKeyCleanup cleanup, IdempotencyKeys keys, JdbcTemplate jdbc,
            MutableClock clock, MeterRegistry meters, ClusterLock lock) {
        this.lock = lock;
        this.cleanup = cleanup;
        this.keys = keys;
        this.jdbc = jdbc;
        this.clock = clock;
        this.meters = meters;
    }

    @BeforeEach
    void clean() {
        clock.set(NOW);
        jdbc.update("delete from idempotency_keys");
    }

    @Test
    void aNewKeyExpiresAfterTheConfiguredTtl() {
        keys.claim(1L, "fresh", 10L, 1);

        Timestamp expires = jdbc.queryForObject("select expires_at from idempotency_keys", Timestamp.class);

        assertThat(expires.toInstant()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void onlyExpiredKeysAreRemoved() {
        key("old-1", NOW.minusSeconds(1));
        key("fresh-1", NOW.plusSeconds(1));
        key("fresh-2", NOW.plus(Duration.ofHours(5)));

        int removed = cleanup.removeExpired();

        assertThat(removed).isEqualTo(1);
        assertThat(remaining()).containsExactlyInAnyOrder("fresh-1", "fresh-2");
    }

    @Test
    void aKeyThatExpiresExactlyNowIsStillKept() {
        key("edge", NOW);

        assertThat(cleanup.removeExpired()).isZero();
        assertThat(remaining()).containsExactly("edge");
    }

    @Test
    void severalBatchesAreRunUntilNothingIsLeftToRemove() {
        for (int i = 0; i < 5; i++) {
            key("old-" + i, NOW.minus(Duration.ofHours(1 + i)));
        }
        key("fresh", NOW.plus(Duration.ofHours(1)));
        double before = meters.counter("cleanup.idempotency.keys.deleted").count();

        int removed = cleanup.removeExpired();   // batch-size is 2: three rounds for five rows

        assertThat(removed).isEqualTo(5);
        assertThat(remaining()).containsExactly("fresh");
        assertThat(meters.counter("cleanup.idempotency.keys.deleted").count() - before).isEqualTo(5.0);
    }

    @Test
    void anEmptyTableIsFine() {
        assertThat(cleanup.removeExpired()).isZero();
    }

    @Test
    void aSecondInstanceCannotTakeTheLockWhileTheFirstHoldsIt() throws Exception {
        // ShedLock lets the same thread in again, so the "other instance" is another thread
        var outer = lock.runExclusive("test-lock", () -> {
            try (var other = java.util.concurrent.Executors.newSingleThreadExecutor()) {
                return other.submit(() -> lock.runExclusive("test-lock", () -> "inner")).get();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        assertThat(outer).isPresent();
        assertThat(outer.get()).isEmpty();   // the other thread found the lock taken and did not run
    }

    private java.util.List<String> remaining() {
        return jdbc.queryForList("select idempotency_key from idempotency_keys", String.class);
    }

    private void key(String name, Instant expiresAt) {
        jdbc.update("insert into idempotency_keys (user_id, idempotency_key, event_id, seats, created_at, expires_at) "
                        + "values (1, ?, 10, 1, ?, ?)",
                name, Timestamp.from(NOW.minus(Duration.ofDays(2))), Timestamp.from(expiresAt));
    }
}
