package com.example.eventsbooking.favorite;

import com.example.eventsbooking.event.Event;
import com.example.eventsbooking.event.EventNotFoundException;
import com.example.eventsbooking.event.EventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class FavoriteServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");

    private final FavoriteRepository favorites = mock(FavoriteRepository.class);
    private final EventRepository events = mock(EventRepository.class);
    private final FavoriteService service =
            new FavoriteService(favorites, events, Clock.fixed(NOW, ZoneOffset.UTC));

    // ---------- add ----------

    @Test
    void addInsertsWithTimeFromTheClockAndReportsCreation() {
        when(favorites.insertIfAbsent(7L, 5L, NOW)).thenReturn(1);

        assertThat(service.add(7L, 5L)).isTrue();

        verify(favorites).insertIfAbsent(7L, 5L, NOW);
        verifyNoInteractions(events);
    }

    @Test
    void addingTwiceIsIdempotentAndReportsNoCreation() {
        when(favorites.insertIfAbsent(7L, 5L, NOW)).thenReturn(0);
        when(events.existsById(5L)).thenReturn(true);

        assertThat(service.add(7L, 5L)).isFalse();
    }

    @Test
    void addingAnUnknownEventFailsWith404Exception() {
        when(favorites.insertIfAbsent(7L, 99L, NOW)).thenReturn(0);
        when(events.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.add(7L, 99L)).isInstanceOf(EventNotFoundException.class);
    }

    // ---------- remove ----------

    @Test
    void removeDeletesOnlyTheCallersRow() {
        service.remove(7L, 5L);

        verify(favorites).deleteByUserIdAndEventId(7L, 5L);
    }

    @Test
    void removeOfAMissingFavoriteDoesNotFail() {
        when(favorites.deleteByUserIdAndEventId(7L, 5L)).thenReturn(0);

        service.remove(7L, 5L); // no exception
    }

    // ---------- list ----------

    @Test
    void listReturnsOnlyTheCallersEvents() {
        Pageable pageable = PageRequest.of(0, 10);
        Event event = new Event("Jazz", "d", Instant.parse("2099-01-01T00:00:00Z"), 5, 1L);
        Page<Event> page = new PageImpl<>(List.of(event), pageable, 1);
        when(favorites.findEventsByUserId(7L, pageable)).thenReturn(page);

        assertThat(service.list(7L, pageable)).isSameAs(page);
    }

    // ---------- favoriteEventIds (isFavorite flag) ----------

    @Test
    void guestHasNoFavoritesAndCostsNoQuery() {
        assertThat(service.favoriteEventIds(null, List.of(1L, 2L))).isEmpty();

        verifyNoInteractions(favorites);
    }

    @Test
    void noEventsCostNoQuery() {
        assertThat(service.favoriteEventIds(7L, List.of())).isEmpty();

        verifyNoInteractions(favorites);
    }

    @Test
    void favoriteIdsAreResolvedWithOneQueryForTheWholePage() {
        when(favorites.findFavoredEventIds(7L, List.of(1L, 2L, 3L))).thenReturn(Set.of(2L));

        assertThat(service.favoriteEventIds(7L, List.of(1L, 2L, 3L))).containsExactly(2L);

        verify(favorites).findFavoredEventIds(7L, List.of(1L, 2L, 3L));
        verifyNoMoreInteractions(favorites);
        org.mockito.Mockito.verify(events, org.mockito.Mockito.never()).existsById(anyLong());
    }
}
