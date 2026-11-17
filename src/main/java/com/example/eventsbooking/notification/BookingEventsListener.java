package com.example.eventsbooking.notification;

import com.example.eventsbooking.booking.BookingCreated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes {@link BookingCreated} and stores a notification. Delivery is at-least-once, so the same message can
 * arrive twice; {@link NotificationRepository#insertIfAbsent} makes handling idempotent.
 */
@Component
class BookingEventsListener {

    private static final Logger log = LoggerFactory.getLogger(BookingEventsListener.class);

    private final NotificationRepository notifications;
    private final JsonMapper mapper;

    BookingEventsListener(NotificationRepository notifications, JsonMapper mapper) {
        this.notifications = notifications;
        this.mapper = mapper;
    }

    @KafkaListener(topics = BookingCreated.TOPIC, groupId = "notifications",
            autoStartup = "${app.kafka.listener.auto-startup:true}")
    void onBookingCreated(String payload) {
        BookingCreated event = parse(payload);
        String message = "Booking %d confirmed: user %d booked event %d"
                .formatted(event.bookingId(), event.userId(), event.targetEventId());
        int inserted = notifications.insertIfAbsent(event.eventId(), event.bookingId(), event.userId(), message);
        if (inserted == 0) {
            log.info("Duplicate BookingCreated {} ignored", event.eventId());
        }
    }

    private BookingCreated parse(String payload) {
        try {
            BookingCreated event = mapper.readValue(payload, BookingCreated.class);
            if (event.eventId() == null) {
                throw new InvalidBookingEventException("BookingCreated without eventId", null);
            }
            return event;
        } catch (JacksonException e) {
            // payload is deliberately not logged: it may contain user data
            throw new InvalidBookingEventException("Unreadable BookingCreated message", e);
        }
    }
}
