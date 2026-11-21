package com.example.eventsbooking.cleanup;

import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Runs a job on one application instance at a time (ShedLock over the {@code shedlock} table). An instance that does
 * not get the lock skips the run: the instance holding it does the work.
 */
public class ClusterLock {

    /** A crashed instance must not block the job forever: its lock is released after this long. */
    static final Duration LOCK_AT_MOST = Duration.ofMinutes(30);
    /** Two instances waking up at the same second do not run the job twice in a row. */
    static final Duration LOCK_AT_LEAST = Duration.ofMinutes(1);

    private final LockingTaskExecutor executor;
    private final Clock clock;

    public ClusterLock(LockProvider provider, Clock clock) {
        this.executor = new DefaultLockingTaskExecutor(provider);
        this.clock = clock;
    }

    /** The result of the work, or empty if another instance holds the lock. */
    public <T> Optional<T> runExclusive(String name, Supplier<T> work) {
        try {
            LockingTaskExecutor.TaskResult<T> result = executor.executeWithLock(
                    (LockingTaskExecutor.TaskWithResult<T>) work::get,
                    new LockConfiguration(clock.instant(), name, LOCK_AT_MOST, LOCK_AT_LEAST));
            return result.wasExecuted() ? Optional.ofNullable(result.getResult()) : Optional.empty();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("Job " + name + " failed", e);
        }
    }
}
