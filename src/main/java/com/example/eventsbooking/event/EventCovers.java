package com.example.eventsbooking.event;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * The cover picture (position 0 of the media list) of many events in one query. The list endpoints use it instead of
 * the lazy media collection, which is not loaded outside a transaction.
 */
@Repository
public class EventCovers {

    private final JdbcClient jdbc;

    public EventCovers(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Cover URL by event id; events without pictures are absent. */
    public Map<Long, String> coverUrls(Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> covers = new HashMap<>();
        jdbc.sql("SELECT event_id, url FROM event_media WHERE position = 0 AND event_id IN (:ids)")
                .param("ids", eventIds)
                .query((rs, n) -> covers.put(rs.getLong("event_id"), rs.getString("url")))
                .list();
        return covers;
    }
}
