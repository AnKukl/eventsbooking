package com.example.eventsbooking.notification;

import com.example.eventsbooking.booking.BookingCreated;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
class KafkaConfig {

    @Bean
    NewTopic bookingEventsTopic() {
        return TopicBuilder.name(BookingCreated.TOPIC).partitions(3).replicas(1).build();
    }

    /**
     * Retries a failing record 3 times, one second apart (a database blip), then logs it and moves on so one bad
     * record cannot block the partition. Unreadable messages are not retried at all. Without a dead letter topic
     * the skipped record is lost; see docs/decisions.md and README.md.
     */
    @Bean
    CommonErrorHandler kafkaErrorHandler() {
        DefaultErrorHandler handler = new DefaultErrorHandler(new FixedBackOff(1000L, 3));
        handler.addNotRetryableExceptions(InvalidBookingEventException.class);
        return handler;
    }
}
