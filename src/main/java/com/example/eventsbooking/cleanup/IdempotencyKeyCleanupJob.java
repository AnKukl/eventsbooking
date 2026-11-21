package com.example.eventsbooking.cleanup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class IdempotencyKeyCleanupJob {

    static final String LOCK_NAME = "idempotency-key-cleanup";
    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyCleanupJob.class);

    private final IdempotencyKeyCleanup cleanup;
    private final ClusterLock lock;

    IdempotencyKeyCleanupJob(IdempotencyKeyCleanup cleanup, ClusterLock lock) {
        this.cleanup = cleanup;
        this.lock = lock;
    }

    @Scheduled(cron = "${app.cleanup.idempotency.cron:0 0 3 * * *}")
    void run() {
        try {
            if (lock.runExclusive(LOCK_NAME, cleanup::removeExpired).isEmpty()) {
                log.info("Idempotency cleanup skipped: another instance is running it");
            }
        } catch (RuntimeException e) {
            // the next scheduled run retries; an exception must not stop the scheduled task
            log.error("Idempotency cleanup failed", e);
        }
    }
}
