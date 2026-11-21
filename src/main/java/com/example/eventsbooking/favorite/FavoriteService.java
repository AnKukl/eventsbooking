package com.example.eventsbooking.favorite;

import com.example.eventsbooking.event.Event;
import com.example.eventsbooking.event.EventNotFoundException;
import com.example.eventsbooking.event.EventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class FavoriteService {

    private final FavoriteRepository favorites;
    private final EventRepository events;
    private final Clock clock;

    public FavoriteService(FavoriteRepository favorites, EventRepository events, Clock clock) {
        this.favorites = favorites;
        this.events = events;
        this.clock = clock;
    }

    /** Idempotent. Returns true if the favorite was created, false if it already existed. */
    @Transactional
    public boolean add(long userId, long eventId) {
        boolean created = favorites.insertIfAbsent(userId, eventId, clock.instant()) == 1;
        if (!created && !events.existsById(eventId)) {
            throw new EventNotFoundException(eventId);
        }
        return created;
    }

    /** Idempotent: removing something that is not there is not an error. */
    @Transactional
    public void remove(long userId, long eventId) {
        favorites.deleteByUserIdAndEventId(userId, eventId);
    }

    public Page<Event> list(long userId, Pageable pageable) {
        return favorites.findEventsByUserId(userId, pageable);
    }

    /** Which of the given events the user has favorited: one query per page. A guest (null) has none. */
    public Set<Long> favoriteEventIds(Long userId, Collection<Long> eventIds) {
        if (userId == null || eventIds.isEmpty()) {
            return Set.of();
        }
        return favorites.findFavoredEventIds(userId, eventIds);
    }
}
