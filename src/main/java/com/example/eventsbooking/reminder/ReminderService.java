package com.example.eventsbooking.reminder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** Creates the "24 hours before" and "2 hours before" notifications for everyone with a booking or a favorite. */
@Service
public class ReminderService {

    private final ReminderRepository reminders;
    private final Clock clock;

    ReminderService(ReminderRepository reminders, Clock clock) {
        this.reminders = reminders;
        this.clock = clock;
    }

    /** Safe to call as often as needed and from several instances: every reminder is produced once. */
    @Transactional
    public int sendDueReminders() {
        Instant now = clock.instant();
        return reminders.sendDayBefore(now) + reminders.sendTwoHoursBefore(now);
    }
}
