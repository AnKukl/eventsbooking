package com.example.eventsbooking.booking;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.UUID;

/**
 * Publishes {@link BookingCreated}. It is called by {@link BookingService} only <em>after</em> the booking
 * transaction has committed, so consumers never hear about a booking that was rolled back.
 *
 * <p>Failure to publish is logged and swallowed: the booking is already committed and must not be reported to
 * the client as failed. The price is that a crash between commit and send loses the event (the dual-write
 * problem); the transactional outbox described in docs/decisions.md closes that gap.
 */
@Component
public class BookingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BookingEventPublisher.class);

    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper mapper;
    private final Clock clock;

    public BookingEventPublisher(KafkaTemplate<String, String> kafka, JsonMapper mapper, Clock clock) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.clock = clock;
    }

    public void publish(Booking booking) {
        BookingCreated event = new BookingCreated(UUID.randomUUID(), booking.getId(), booking.getEventId(),
                booking.getUserId(), clock.instant());
        try {
            // key = bookingId: events of one booking stay ordered within a partition
            kafka.send(new ProducerRecord<>(BookingCreated.TOPIC, String.valueOf(event.bookingId()),
                            mapper.writeValueAsString(event)))
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("BookingCreated {} for booking {} was not delivered",
                                    event.eventId(), event.bookingId(), error);
                        }
                    });
        } catch (RuntimeException e) {
            log.error("BookingCreated {} for booking {} could not be published",
                    event.eventId(), event.bookingId(), e);
        }
    }
}
