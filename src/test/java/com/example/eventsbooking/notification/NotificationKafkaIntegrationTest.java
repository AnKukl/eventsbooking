package com.example.eventsbooking.notification;

import com.example.eventsbooking.booking.BookingService;
import com.example.eventsbooking.event.Event;
import com.example.eventsbooking.event.EventRequest;
import com.example.eventsbooking.event.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Real Kafka and PostgreSQL in containers: publisher, topic, consumer and idempotency end to end. */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true",
        "app.kafka.listener.auto-startup=true",
        "spring.kafka.admin.auto-create=true",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.producer.properties.max.block.ms=10000"
})
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class NotificationKafkaIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

    private final EventService eventService;
    private final BookingService bookingService;
    private final KafkaTemplate<String, String> kafka;
    private final JdbcTemplate jdbc;

    NotificationKafkaIntegrationTest(EventService eventService, BookingService bookingService,
            KafkaTemplate<String, String> kafka, JdbcTemplate jdbc) {
        this.eventService = eventService;
        this.bookingService = bookingService;
        this.kafka = kafka;
        this.jdbc = jdbc;
    }

    @Test
    void bookingProducesExactlyOneNotification() {
        Event event = eventService.create(new EventRequest("Kafka night", null,
                Instant.now().plus(10, ChronoUnit.DAYS), 5), 1L);

        long bookingId = bookingService.book(event.getId(), 42L).getId();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(count("booking_id = ?", bookingId)).isEqualTo(1));
        assertThat(jdbc.queryForObject("select user_id from notifications where booking_id = ?",
                Long.class, bookingId)).isEqualTo(42L);
    }

    @Test
    void redeliveredEventWithTheSameEventIdDoesNotCreateASecondNotification() {
        String json = bookingCreatedJson(UUID.randomUUID(), 9001L, 7L);

        kafka.send("booking-events", "9001", json);
        kafka.send("booking-events", "9001", json);   // duplicate delivery
        waitUntilProcessed();

        assertThat(count("booking_id = ?", 9001L)).isEqualTo(1);
    }

    @Test
    void differentEventIdsAreDifferentNotifications() {
        kafka.send("booking-events", "9002", bookingCreatedJson(UUID.randomUUID(), 9002L, 7L));
        kafka.send("booking-events", "9002", bookingCreatedJson(UUID.randomUUID(), 9002L, 7L));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(count("booking_id = ?", 9002L)).isEqualTo(2));
    }

    @Test
    void poisonMessageDoesNotBlockTheConsumer() {
        kafka.send("booking-events", "bad", "this is not json");
        kafka.send("booking-events", "9003", bookingCreatedJson(UUID.randomUUID(), 9003L, 7L));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(count("booking_id = ?", 9003L)).isEqualTo(1));
    }

    /** A marker event sent after the duplicates: once it is stored, everything before it was processed. */
    private void waitUntilProcessed() {
        long marker = 9999L + System.nanoTime() % 1000;
        kafka.send("booking-events", "marker", bookingCreatedJson(UUID.randomUUID(), marker, 1L));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(count("booking_id = ?", marker)).isEqualTo(1));
    }

    private long count(String where, Object arg) {
        return jdbc.queryForObject("select count(*) from notifications where " + where, Long.class, arg);
    }

    private static String bookingCreatedJson(UUID eventId, long bookingId, long userId) {
        return """
                {"eventId":"%s","bookingId":%d,"targetEventId":1,"userId":%d,"occurredAt":"2030-01-01T10:00:00Z"}
                """.formatted(eventId, bookingId, userId);
    }
}
