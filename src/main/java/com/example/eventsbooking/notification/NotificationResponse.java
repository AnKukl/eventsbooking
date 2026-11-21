package com.example.eventsbooking.notification;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        String type,
        String title,
        String body,
        Long targetEventId,
        Instant readAt,
        Instant createdAt) {

    static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getTitle(),
                notification.getBody(), notification.getTargetEventId(), notification.getReadAt(),
                notification.getCreatedAt());
    }
}
