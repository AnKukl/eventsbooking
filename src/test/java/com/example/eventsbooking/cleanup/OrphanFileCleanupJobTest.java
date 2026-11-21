package com.example.eventsbooking.cleanup;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrphanFileCleanupJobTest {

    private final OrphanFileCleanup cleanup = mock(OrphanFileCleanup.class);
    private final ClusterLock lock = mock(ClusterLock.class);
    private final OrphanFileCleanupJob job = new OrphanFileCleanupJob(cleanup, lock);

    @Test
    @SuppressWarnings("unchecked")
    void theCleanupRunsInsideTheClusterLock() {
        when(lock.runExclusive(eq(OrphanFileCleanupJob.LOCK_NAME), any(Supplier.class))).thenAnswer(call -> {
            Supplier<?> work = call.getArgument(1);
            return Optional.ofNullable(work.get());
        });
        when(cleanup.run()).thenReturn(new OrphanFileCleanup.Result(0, 0, 0, true));

        job.run();

        verify(cleanup).run();
    }

    @Test
    @SuppressWarnings("unchecked")
    void whenAnotherInstanceHoldsTheLockNothingRuns() {
        when(lock.runExclusive(eq(OrphanFileCleanupJob.LOCK_NAME), any(Supplier.class))).thenReturn(Optional.empty());

        job.run();

        verify(cleanup, never()).run();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aFailingRunDoesNotKillTheScheduler() {
        when(lock.runExclusive(eq(OrphanFileCleanupJob.LOCK_NAME), any(Supplier.class)))
                .thenThrow(new IllegalStateException("db down"));

        assertThatCode(job::run).doesNotThrowAnyException();
    }
}
