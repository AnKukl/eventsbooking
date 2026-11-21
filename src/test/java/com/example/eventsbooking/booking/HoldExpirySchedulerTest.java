package com.example.eventsbooking.booking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class HoldExpirySchedulerTest {

    private final HoldExpiryService service = mock(HoldExpiryService.class);
    private final HoldExpiryScheduler scheduler = new HoldExpiryScheduler(service);

    @Test
    void everyTickExpiresTheHolds() {
        scheduler.run();

        verify(service).expireHolds();
    }

    @Test
    void aFailingTickDoesNotKillTheScheduler() {
        doThrow(new IllegalStateException("version conflict")).when(service).expireHolds();

        assertThatCode(scheduler::run).doesNotThrowAnyException();
    }
}
