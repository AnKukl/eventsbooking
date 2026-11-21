package com.example.eventsbooking.waitlist;

import com.example.eventsbooking.event.Event;
import com.example.eventsbooking.event.EventRepository;
import com.example.eventsbooking.event.SeatReleased;
import com.example.eventsbooking.notification.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WaitlistNotifierTest {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private static final UUID RELEASE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final WaitlistRepository waitlist = mock(WaitlistRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final EventRepository events = mock(EventRepository.class);
    private final WaitlistNotifier notifier =
            new WaitlistNotifier(waitlist, notifications, events, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void anEmptyQueueNotifiesNobody() {
        existingEvent();
        when(waitlist.findNextToNotify(5L)).thenReturn(Optional.empty());

        notifier.notifyNext(new SeatReleased(5L, RELEASE, 1));

        verify(notifications, never()).insertSeatAvailable(any(), anyLong(), anyLong(), anyString(), anyString(), any());
        verify(waitlist, never()).markNotified(anyLong(), any());
    }

    @Test
    void onlyTheFirstInTheQueueIsNotifiedAndMarked() {
        existingEvent();
        when(waitlist.findNextToNotify(5L)).thenReturn(Optional.of(entry(11L, 7L)));
        when(notifications.insertSeatAvailable(any(), anyLong(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(1);

        notifier.notifyNext(new SeatReleased(5L, RELEASE, 1));

        verify(notifications).insertSeatAvailable(eq(RELEASE), eq(5L), eq(7L), anyString(),
                org.mockito.ArgumentMatchers.contains("Jazz night"), eq(NOW));
        verify(waitlist).markNotified(11L, NOW);
        verify(waitlist, times(1)).findNextToNotify(5L);
    }

    @Test
    void aRepeatedReleaseIdCreatesNoSecondNotificationAndMarksNobody() {
        existingEvent();
        when(waitlist.findNextToNotify(5L)).thenReturn(Optional.of(entry(12L, 8L)));
        when(notifications.insertSeatAvailable(any(), anyLong(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(0);   // the unique key is already taken: this release was handled

        notifier.notifyNext(new SeatReleased(5L, RELEASE, 1));

        verify(waitlist, never()).markNotified(anyLong(), any());
    }

    @Test
    void severalSeatsNotifyTheQueueInOrderWithDistinctDeterministicKeys() {
        existingEvent();
        when(waitlist.findNextToNotify(5L)).thenReturn(Optional.of(entry(11L, 7L)), Optional.of(entry(12L, 8L)),
                Optional.empty());
        when(notifications.insertSeatAvailable(any(), anyLong(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(1);

        notifier.notifyNext(new SeatReleased(5L, RELEASE, 3));

        ArgumentCaptor<UUID> keys = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<Long> users = ArgumentCaptor.forClass(Long.class);
        verify(notifications, times(2)).insertSeatAvailable(keys.capture(), anyLong(), users.capture(), anyString(),
                anyString(), any());
        assertThat(users.getAllValues()).containsExactly(7L, 8L);
        assertThat(keys.getAllValues().get(0)).isEqualTo(RELEASE);
        assertThat(keys.getAllValues().get(1)).isNotEqualTo(RELEASE);
        verify(waitlist).markNotified(11L, NOW);
        verify(waitlist).markNotified(12L, NOW);

        // the same release handled again produces the very same keys, so it cannot notify anybody twice
        assertThat(WaitlistNotifier.keyFor(RELEASE, 1)).isEqualTo(keys.getAllValues().get(1));
    }

    @Test
    void aDuplicateStopsTheWholeBatch() {
        existingEvent();
        when(waitlist.findNextToNotify(5L)).thenReturn(Optional.of(entry(11L, 7L)), Optional.of(entry(12L, 8L)));
        when(notifications.insertSeatAvailable(any(), anyLong(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(0);

        notifier.notifyNext(new SeatReleased(5L, RELEASE, 3));

        verify(notifications, times(1)).insertSeatAvailable(any(), anyLong(), anyLong(), anyString(), anyString(), any());
        verify(waitlist, never()).markNotified(anyLong(), any());
    }

    @Test
    void aDeletedEventNotifiesNobody() {
        when(events.findById(5L)).thenReturn(Optional.empty());

        notifier.notifyNext(new SeatReleased(5L, RELEASE, 1));

        verify(waitlist, never()).findNextToNotify(anyLong());
    }

    @Test
    void keysAreDistinctPerIndexAndStable() {
        List<UUID> keys = List.of(WaitlistNotifier.keyFor(RELEASE, 0), WaitlistNotifier.keyFor(RELEASE, 1),
                WaitlistNotifier.keyFor(RELEASE, 2));

        assertThat(keys).doesNotHaveDuplicates();
        assertThat(keys.get(0)).isEqualTo(RELEASE);
        assertThat(WaitlistNotifier.keyFor(RELEASE, 2)).isEqualTo(keys.get(2));
    }

    private void existingEvent() {
        Event event = new Event("Jazz night", null, Instant.parse("2099-01-01T00:00:00Z"), 1, 1L);
        ReflectionTestUtils.setField(event, "id", 5L);
        when(events.findById(5L)).thenReturn(Optional.of(event));
    }

    private static WaitlistEntry entry(long id, long userId) {
        WaitlistEntry entry = new WaitlistEntry();
        ReflectionTestUtils.setField(entry, "id", id);
        ReflectionTestUtils.setField(entry, "userId", userId);
        ReflectionTestUtils.setField(entry, "eventId", 5L);
        return entry;
    }
}
