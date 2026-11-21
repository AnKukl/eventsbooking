package com.example.eventsbooking.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Per-type channel choices. A type the user never touched is on for both channels. The choices are stored and
 * exposed; delivery over email or push is not implemented yet, the in-app feed always receives the notification.
 */
@Service
@Transactional(readOnly = true)
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository repository;

    public NotificationPreferenceService(NotificationPreferenceRepository repository) {
        this.repository = repository;
    }

    /** Every notification type, in enum order, with the stored choice or the default. */
    public List<NotificationPreference> get(long userId) {
        Map<NotificationType, NotificationPreference> stored = new EnumMap<>(NotificationType.class);
        repository.findByUserId(userId).forEach(preference -> stored.put(preference.type(), preference));
        return Arrays.stream(NotificationType.values())
                .map(type -> stored.getOrDefault(type, new NotificationPreference(type, true, true)))
                .toList();
    }

    /** Stores the given types; types not mentioned keep their current choice. */
    @Transactional
    public List<NotificationPreference> update(long userId, List<NotificationPreference> changes) {
        changes.forEach(change -> repository.upsert(userId, change));
        return get(userId);
    }
}
