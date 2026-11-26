package com.example.eventsbooking.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notifications;
    private final java.time.Clock clock;

    public NotificationService(NotificationRepository notifications, java.time.Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    public long unreadCount(long userId) {
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }

    /** Marks the user's notifications read: the given ones, or all of them when {@code ids} is empty. */
    @Transactional
    public int markRead(long userId, java.util.Collection<Long> ids) {
        java.time.Instant now = clock.instant();
        return ids == null || ids.isEmpty() ? notifications.markAllRead(userId, now) : notifications.markRead(userId, ids, now);
    }

    /** The user's own notifications, newest first. */
    public Page<Notification> feed(long userId, Pageable pageable) {
        return notifications.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);
    }
}
