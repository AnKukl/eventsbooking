package com.example.eventsbooking.cleanup;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Settings of the periodic cleanup jobs ({@code app.cleanup.*}). */
@ConfigurationProperties("app.cleanup")
public record CleanupProperties(@DefaultValue Idempotency idempotency, @DefaultValue Orphans orphans) {

    /**
     * @param cron      when the job runs
     * @param batchSize rows removed by one DELETE, so a big backlog never holds long locks
     * @param pause     rest between two batches, so the job does not starve the booking traffic
     */
    public record Idempotency(@DefaultValue("0 0 3 * * *") String cron, @DefaultValue("1000") int batchSize,
            @DefaultValue("PT0.1S") Duration pause) {
    }

    /**
     * @param cron      when the job runs
     * @param grace     how old a file or a PENDING row must be before it counts as an orphan; an upload in progress is
     *                  always younger than this
     * @param dryRun    only count and log, remove nothing (the default until the numbers have been looked at)
     * @param batchSize rows per query, and file keys per lookup
     */
    public record Orphans(@DefaultValue("0 30 3 * * *") String cron, @DefaultValue("PT24H") Duration grace,
            @DefaultValue("true") boolean dryRun, @DefaultValue("500") int batchSize) {
    }
}
