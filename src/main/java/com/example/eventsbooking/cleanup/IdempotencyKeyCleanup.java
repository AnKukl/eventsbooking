package com.example.eventsbooking.cleanup;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Removes idempotency keys whose time is up, in small batches. */
@Service
public class IdempotencyKeyCleanup {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyCleanup.class);

    private final JdbcClient jdbc;
    private final Clock clock;
    private final CleanupProperties.Idempotency settings;
    private final Counter deleted;

    IdempotencyKeyCleanup(JdbcClient jdbc, Clock clock, CleanupProperties properties, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.settings = properties.idempotency();
        this.deleted = Counter.builder("cleanup.idempotency.keys.deleted")
                .description("Expired idempotency keys removed by the cleanup job").register(meters);
    }

    /** Runs batches until one removes nothing; returns how many keys were removed in total. */
    public int removeExpired() {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        int total = 0;
        int batches = 0;
        int removed;
        do {
            removed = removeBatch(now);
            if (removed > 0) {
                total += removed;
                batches++;
                deleted.increment(removed);
                pause();
            }
        } while (removed > 0);
        log.info("Idempotency cleanup finished: removed {} expired keys in {} batches", total, batches);
        return total;
    }

    private int removeBatch(OffsetDateTime now) {
        return jdbc.sql("""
                DELETE FROM idempotency_keys WHERE id IN (
                    SELECT id FROM idempotency_keys WHERE expires_at < :now ORDER BY expires_at LIMIT :batch)
                """)
                .param("now", now).param("batch", settings.batchSize())
                .update();
    }

    private void pause() {
        if (settings.pause().isZero()) {
            return;
        }
        try {
            Thread.sleep(settings.pause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
