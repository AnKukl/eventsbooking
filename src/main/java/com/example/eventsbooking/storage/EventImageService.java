package com.example.eventsbooking.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Upload order: the file is written under a temporary name, the PENDING row is inserted, the file gets its final name,
 * and only then does the transaction commit. If anything fails on the way the files are removed (best effort: a failed
 * removal leaves an orphan that the orphan cleanup finds later).
 */
@Service
public class EventImageService {

    private static final Logger log = LoggerFactory.getLogger(EventImageService.class);

    private final FileStorage storage;
    private final EventImageRepository images;
    private final Clock clock;

    public EventImageService(FileStorage storage, EventImageRepository images, Clock clock) {
        this.storage = storage;
        this.images = images;
        this.clock = clock;
    }

    /** Stores the picture and returns its final key; the row stays PENDING until {@link #attach}. */
    @Transactional
    public String store(byte[] content, ImageType type) {
        String temporary = storage.saveTemporary(content);
        String key = storage.newKey(type);
        try {
            images.insertPending(key, clock.instant());
            storage.move(temporary, key);
            return key;
        } catch (RuntimeException e) {
            deleteQuietly(temporary);
            deleteQuietly(key);
            throw e;
        }
    }

    /** The pictures now belong to the event. */
    @Transactional
    public void attach(long eventId, List<String> keys) {
        images.attach(eventId, keys);
    }

    /** For callers that fail after {@link #store}: removes the file; the row goes with the rolled-back transaction. */
    public void discard(String key) {
        deleteQuietly(key);
    }

    private void deleteQuietly(String name) {
        try {
            storage.delete(name);
        } catch (RuntimeException e) {
            log.warn("Could not remove file {}; the orphan cleanup will find it", name, e);
        }
    }
}
