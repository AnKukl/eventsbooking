package com.example.eventsbooking.booking;

import com.example.eventsbooking.event.Event;
import com.example.eventsbooking.event.EventRepository;
import com.example.eventsbooking.event.EventRequest;
import com.example.eventsbooking.event.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The application clock is replaced by a fixed one, so the 24-hour rule is tested without real time. */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true"
})
@AutoConfigureMockMvc
@Testcontainers
@WithMockUser(username = "1", roles = "USER")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class BookingCancellationIntegrationTest {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    private final EventService eventService;
    private final EventRepository eventRepository;
    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final JdbcTemplate jdbc;
    private final MockMvc mockMvc;

    BookingCancellationIntegrationTest(EventService eventService, EventRepository eventRepository,
            BookingService bookingService, BookingRepository bookingRepository,
            JdbcTemplate jdbc, MockMvc mockMvc) {
        this.eventService = eventService;
        this.eventRepository = eventRepository;
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.jdbc = jdbc;
        this.mockMvc = mockMvc;
    }

    @Test
    void cancellationChangesStatusAndReturnsTheSeat() {
        Event event = newEvent(1, NOW.plus(Duration.ofDays(10)));
        Booking booking = bookingService.book(event.getId(), 1L);
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getBookedCount()).isEqualTo(1);

        Booking cancelled = bookingService.cancel(event.getId(), booking.getId(), 1L);

        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(cancelled.getCancelledAt()).isEqualTo(NOW);
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getBookedCount()).isZero();
    }

    @Test
    void returnedSeatCanBeBookedByAnotherUser() {
        Event event = newEvent(1, NOW.plus(Duration.ofDays(10)));
        Booking first = bookingService.book(event.getId(), 1L);
        bookingService.cancel(event.getId(), first.getId(), 1L);

        Booking second = bookingService.book(event.getId(), 2L);

        assertThat(second.getStatus()).isEqualTo(BookingStatus.ACTIVE);
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getBookedCount()).isEqualTo(1);
    }

    @Test
    void userCanBookAgainAfterCancelling() {
        Event event = newEvent(5, NOW.plus(Duration.ofDays(10)));
        Booking first = bookingService.book(event.getId(), 1L);
        bookingService.cancel(event.getId(), first.getId(), 1L);

        Booking again = bookingService.book(event.getId(), 1L);

        assertThat(again.getId()).isNotEqualTo(first.getId());
        assertThat(bookingRepository.countByEventId(event.getId())).isEqualTo(2);
    }

    @Test
    void uniquenessAppliesToActiveBookingsOnly() {
        Event event = newEvent(5, NOW.plus(Duration.ofDays(10)));
        String insert = "insert into bookings (event_id, user_id, status) values (?, ?, ?)";
        jdbc.update(insert, event.getId(), 1L, "CANCELLED");
        jdbc.update(insert, event.getId(), 1L, "ACTIVE");

        assertThatThrownBy(() -> jdbc.update(insert, event.getId(), 1L, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cancellationExactly24HoursBeforeStartIsAllowedOverHttp() throws Exception {
        Event event = newEvent(5, NOW.plus(Duration.ofHours(24)));
        Booking booking = bookingService.book(event.getId(), 1L);

        mockMvc.perform(post(cancelUrl(event, booking)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancellation23Hours59MinutesBeforeStartIsRejectedOverHttpAndSeatStaysTaken() throws Exception {
        Event event = newEvent(5, NOW.plus(Duration.ofHours(23).plusMinutes(59)));
        Booking booking = bookingService.book(event.getId(), 1L);

        mockMvc.perform(post(cancelUrl(event, booking)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Cancellation not allowed"));

        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.ACTIVE);
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getBookedCount()).isEqualTo(1);
    }

    @Test
    void parallelCancellationsOfTheSameBookingReturnTheSeatOnlyOnce() throws Exception {
        Event event = newEvent(5, NOW.plus(Duration.ofDays(10)));
        Booking booking = bookingService.book(event.getId(), 1L);

        List<Object> results = runInParallel(
                () -> bookingService.cancel(event.getId(), booking.getId(), 1L),
                () -> bookingService.cancel(event.getId(), booking.getId(), 1L));

        assertThat(results).filteredOn(r -> r instanceof Booking).hasSize(1);
        assertThat(results).filteredOn(r -> r instanceof BookingAlreadyCancelledException).hasSize(1);
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getBookedCount()).isZero();
    }

    @Test
    void anotherUserCannotCancelSomeonesBookingAndTheSeatStaysTaken() throws Exception {
        Event event = newEvent(5, NOW.plus(Duration.ofDays(10)));
        Booking booking = bookingService.book(event.getId(), 2L);   // belongs to user 2

        mockMvc.perform(post(cancelUrl(event, booking)))            // the request is made by user 1
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Booking not found"));

        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.ACTIVE);
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getBookedCount()).isEqualTo(1);
    }

    @Test
    void startedEventCannotBeBookedOverHttp() throws Exception {
        Event event = newEvent(5, NOW.minus(Duration.ofHours(1)));

        mockMvc.perform(post("/api/events/" + event.getId() + "/bookings"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Event already started"));

        assertThat(eventRepository.findById(event.getId()).orElseThrow().getBookedCount()).isZero();
    }

    @Test
    void bookingOverHttpIsMadeForTheAuthenticatedUserAndListedInMyBookings() throws Exception {
        Event event = newEvent(5, NOW.plus(Duration.ofDays(10)));

        mockMvc.perform(post("/api/events/" + event.getId() + "/bookings"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(1));
        bookingService.book(event.getId(), 2L);   // someone else's booking must not show up

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/bookings/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.eventId==" + event.getId() + ")].userId").value(1))
                .andExpect(jsonPath("$.content[?(@.userId==2)]").isEmpty());
    }

    private static String cancelUrl(Event event, Booking booking) {
        return "/api/events/" + event.getId() + "/bookings/" + booking.getId() + "/cancel";
    }

    private Event newEvent(int capacity, Instant startsAt) {
        return eventService.create(new EventRequest("Cancel event", null, startsAt, capacity), 1L);
    }

    private static List<Object> runInParallel(Callable<?>... tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.length);
        try {
            CountDownLatch ready = new CountDownLatch(tasks.length);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<?> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await();
            go.countDown();

            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                try {
                    results.add(future.get());
                } catch (ExecutionException e) {
                    results.add(e.getCause());
                }
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
