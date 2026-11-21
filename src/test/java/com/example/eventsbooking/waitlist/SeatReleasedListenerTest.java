package com.example.eventsbooking.waitlist;

import com.example.eventsbooking.event.SeatReleased;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SeatReleasedListenerTest {

    private final WaitlistNotifier notifier = mock(WaitlistNotifier.class);
    private final SeatReleasedListener listener = new SeatReleasedListener(notifier);
    private final SeatReleased released = new SeatReleased(5L, UUID.randomUUID(), 1);

    @Test
    void handsTheReleaseToTheNotifier() {
        listener.onSeatReleased(released);

        verify(notifier).notifyNext(released);
    }

    @Test
    void aFailureAfterCommitMustNotBreakTheRequestThatAlreadyCommitted() {
        doThrow(new IllegalStateException("db down")).when(notifier).notifyNext(released);

        assertThatCode(() -> listener.onSeatReleased(released)).doesNotThrowAnyException();
    }
}
