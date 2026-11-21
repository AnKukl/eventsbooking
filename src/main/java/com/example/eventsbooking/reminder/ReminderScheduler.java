package com.example.eventsbooking.reminder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final ReminderService service;

    ReminderScheduler(ReminderService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${app.reminders.interval:PT1M}", initialDelayString = "${app.reminders.interval:PT1M}")
    void run() {
        try {
            int created = service.sendDueReminders();
            if (created > 0) {
                log.info("Created {} reminder notifications", created);
            }
        } catch (RuntimeException e) {
            // the next tick retries; an exception must not stop the scheduled task
            log.error("Reminder run failed", e);
        }
    }
}
