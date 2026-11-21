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

class IdempotencyKeyCleanupJobTest {

    private final IdempotencyKeyCleanup cleanup = mock(IdempotencyKeyCleanup.class);
    private final ClusterLock lock = mock(ClusterLock.class);
    private final IdempotencyKeyCleanupJob job = new IdempotencyKeyCleanupJob(cleanup, lock);

    @Test
    @SuppressWarnings("unchecked")
    void theCleanupRunsInsideTheClusterLock() {
        when(lock.runExclusive(eq(IdempotencyKeyCleanupJob.LOCK_NAME), any(Supplier.class))).thenAnswer(call -> {
            Supplier<Integer> work = call.getArgument(1);
            return Optional.of(work.get());
        });
        when(cleanup.removeExpired()).thenReturn(3);

        job.run();

        verify(cleanup).removeExpired();
    }

    @Test
    @SuppressWarnings("unchecked")
    void whenAnotherInstanceHoldsTheLockNothingIsRemoved() {
        when(lock.runExclusive(eq(IdempotencyKeyCleanupJob.LOCK_NAME), any(Supplier.class)))
                .thenReturn(Optional.empty());

        job.run();

        verify(cleanup, never()).removeExpired();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aFailingRunDoesNotKillTheScheduler() {
        when(lock.runExclusive(eq(IdempotencyKeyCleanupJob.LOCK_NAME), any(Supplier.class)))
                .thenThrow(new IllegalStateException("db down"));

        assertThatCode(job::run).doesNotThrowAnyException();
    }
}
