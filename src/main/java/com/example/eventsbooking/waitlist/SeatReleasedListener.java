package com.example.eventsbooking.waitlist;

import com.example.eventsbooking.event.SeatReleased;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to {@link SeatReleased} only once the transaction that freed the seat has committed: a rolled-back
 * cancellation never notifies anybody.
 */
@Component
class SeatReleasedListener {

    private static final Logger log = LoggerFactory.getLogger(SeatReleasedListener.class);

    private final WaitlistNotifier notifier;

    SeatReleasedListener(WaitlistNotifier notifier) {
        this.notifier = notifier;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onSeatReleased(SeatReleased released) {
        try {
            notifier.notifyNext(released);
        } catch (RuntimeException e) {
            // The cancellation has already committed: failing here would turn a successful request into an error.
            // The notification of this release is lost; see docs/decisions.md (011).
            log.error("Could not notify the waitlist of event {} (release {})", released.eventId(),
                    released.releaseId(), e);
        }
    }
}
