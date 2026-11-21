package com.example.eventsbooking.booking;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class BookingEventPublisherTest {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    private final JsonMapper mapper = JsonMapper.builder().build();
    private KafkaTemplate<String, String> kafka;
    private BookingEventPublisher publisher;

    @BeforeEach
    void setUp() {
        kafka = mock(KafkaTemplate.class);
        publisher = new BookingEventPublisher(kafka, mapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void publishesBookingCreatedToTheBookingEventsTopic() throws Exception {
        when(kafka.send(any(ProducerRecord.class))).thenReturn(new CompletableFuture<>());

        publisher.publish(booking(100L, 5L, 7L));

        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo("booking-events");
        assertThat(sent.getValue().key()).isEqualTo("100");

        JsonNode json = mapper.readTree(sent.getValue().value());
        assertThat(json.get("bookingId").asLong()).isEqualTo(100L);
        assertThat(json.get("targetEventId").asLong()).isEqualTo(5L);
        assertThat(json.get("userId").asLong()).isEqualTo(7L);
        assertThat(json.get("eventId").asString()).isNotBlank();
        assertThat(json.get("occurredAt").asString()).isEqualTo("2030-01-01T10:00:00Z");
    }

    @Test
    void everyPublishedEventGetsItsOwnEventId() throws Exception {
        when(kafka.send(any(ProducerRecord.class))).thenReturn(new CompletableFuture<>());

        publisher.publish(booking(100L, 5L, 7L));
        publisher.publish(booking(100L, 5L, 7L));

        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        org.mockito.Mockito.verify(kafka, org.mockito.Mockito.times(2)).send(sent.capture());
        String first = mapper.readTree(sent.getAllValues().get(0).value()).get("eventId").asString();
        String second = mapper.readTree(sent.getAllValues().get(1).value()).get("eventId").asString();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void brokerFailureDoesNotFailTheBooking() {
        when(kafka.send(any(ProducerRecord.class))).thenThrow(new IllegalStateException("broker down"));

        assertThatCode(() -> publisher.publish(booking(100L, 5L, 7L))).doesNotThrowAnyException();
    }

    @Test
    void asynchronousSendFailureDoesNotFailTheBooking() {
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("timeout")));

        assertThatCode(() -> publisher.publish(booking(100L, 5L, 7L))).doesNotThrowAnyException();
    }

    private static Booking booking(long id, long eventId, long userId) {
        Booking booking = new Booking(eventId, userId, 1, java.time.Instant.parse("2030-01-01T10:00:00Z"),
                java.time.Instant.parse("2030-01-01T10:10:00Z"));
        ReflectionTestUtils.setField(booking, "id", id);
        return booking;
    }
}
