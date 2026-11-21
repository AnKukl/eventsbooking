package com.example.eventsbooking.notification;

/** Which channels a user wants for one type of notification. */
public record NotificationPreference(NotificationType type, boolean email, boolean push) {
}
