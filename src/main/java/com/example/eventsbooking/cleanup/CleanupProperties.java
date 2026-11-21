package com.example.eventsbooking.cleanup;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Settings of the periodic cleanup jobs ({@code app.cleanup.*}). */
@ConfigurationProperties("app.cleanup")
public record CleanupProperties(@DefaultValue Idempotency idempotency) {

    /**
     * @param cron      when the job runs
     * @param batchSize rows removed by one DELETE, so a big backlog never holds long locks
     * @param pause     rest between two batches, so the job does not starve the booking traffic
     */
    public record Idempotency(@DefaultValue("0 0 3 * * *") String cron, @DefaultValue("1000") int batchSize,
            @DefaultValue("PT0.1S") Duration pause) {
    }
}
