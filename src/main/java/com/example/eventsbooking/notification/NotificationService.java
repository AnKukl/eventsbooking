package com.example.eventsbooking.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    /** The user's own notifications, newest first. */
    public Page<Notification> feed(long userId, Pageable pageable) {
        return notifications.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);
    }
}
