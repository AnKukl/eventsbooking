package com.example.eventsbooking.reminder;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReminderSchedulerTest {

    private final ReminderService service = mock(ReminderService.class);
    private final ReminderScheduler scheduler = new ReminderScheduler(service);

    @Test
    void everyTickSendsTheDueReminders() {
        scheduler.run();

        verify(service).sendDueReminders();
    }

    @Test
    void aFailureOfOneTickDoesNotEscapeIntoTheSchedulerThread() {
        doThrow(new IllegalStateException("db down")).when(service).sendDueReminders();

        assertThatCode(scheduler::run).doesNotThrowAnyException();
    }
}
