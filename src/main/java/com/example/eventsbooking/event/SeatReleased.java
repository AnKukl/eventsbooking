package com.example.eventsbooking.event;

import java.util.UUID;

/**
 * Seats of an event became free (a booking was cancelled or the capacity was raised). Published inside the transaction
 * that freed them; listeners that must only act on committed data use {@code AFTER_COMMIT}.
 *
 * @param releaseId identifies this release: handling the same release twice must not notify anyone twice
 * @param seats     how many seats were freed (at least one)
 */
public record SeatReleased(long eventId, UUID releaseId, int seats) {
}
