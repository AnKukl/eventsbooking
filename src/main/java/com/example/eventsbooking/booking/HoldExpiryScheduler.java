package com.example.eventsbooking.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class HoldExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryScheduler.class);

    private final HoldExpiryService service;

    HoldExpiryScheduler(HoldExpiryService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${app.booking.hold-expiry-interval:PT30S}",
            initialDelayString = "${app.booking.hold-expiry-interval:PT30S}")
    void run() {
        try {
            int released = service.expireHolds();
            if (released > 0) {
                log.info("Released {} expired holds", released);
            }
        } catch (RuntimeException e) {
            // the next tick retries; an exception must not stop the scheduled task
            log.error("Hold expiry run failed", e);
        }
    }
}
