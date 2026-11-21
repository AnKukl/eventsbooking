package com.example.eventsbooking.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SeatStatusTest {

    private static Event eventWithCapacity(int capacity) {
        return new Event("Jazz", "d", Instant.parse("2099-01-01T00:00:00Z"), capacity, 1L);
    }

    @Test
    void eventWithFreeSeatsIsAvailable() {
        Event event = eventWithCapacity(2);
        event.book();

        assertThat(event.seatStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void fullEventIsSoldOut() {
        Event event = eventWithCapacity(1);
        event.book();

        assertThat(event.seatStatus()).isEqualTo(SeatStatus.SOLD_OUT);
    }

    @Test
    void releasedSeatMakesEventAvailableAgain() {
        Event event = eventWithCapacity(1);
        event.book();
        event.release();

        assertThat(event.seatStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }
}
