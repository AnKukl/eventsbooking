package com.example.eventsbooking.cleanup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class OrphanFileCleanupJob {

    static final String LOCK_NAME = "orphan-file-cleanup";
    private static final Logger log = LoggerFactory.getLogger(OrphanFileCleanupJob.class);

    private final OrphanFileCleanup cleanup;
    private final ClusterLock lock;

    OrphanFileCleanupJob(OrphanFileCleanup cleanup, ClusterLock lock) {
        this.cleanup = cleanup;
        this.lock = lock;
    }

    @Scheduled(cron = "${app.cleanup.orphans.cron:0 30 3 * * *}")
    void run() {
        try {
            if (lock.runExclusive(LOCK_NAME, cleanup::run).isEmpty()) {
                log.info("Orphan cleanup skipped: another instance is running it");
            }
        } catch (RuntimeException e) {
            log.error("Orphan cleanup failed", e);
        }
    }
}
