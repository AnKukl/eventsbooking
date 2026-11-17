package com.example.eventsbooking.booking;

import com.example.eventsbooking.event.Event;
import com.example.eventsbooking.event.EventFullException;
import com.example.eventsbooking.event.EventRepository;
import com.example.eventsbooking.event.EventRequest;
import com.example.eventsbooking.event.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true"
})
@AutoConfigureMockMvc
@Testcontainers
@WithMockUser(roles = "USER")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class BookingConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    private final EventService eventService;
    private final EventRepository eventRepository;
    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final JdbcTemplate jdbc;
    private final MockMvc mockMvc;

    BookingConcurrencyIntegrationTest(EventService eventService, EventRepository eventRepository,
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
    void twoParallelBookingsOfTheLastSeatOneSucceedsAndOneFails() throws Exception {
        Event event = newEvent(2);
        bookingService.book(event.getId(), 1L); // one seat left

        List<Object> results = runInParallel(
                () -> bookingService.book(event.getId(), 2L),
                () -> bookingService.book(event.getId(), 3L));

        assertThat(results).filteredOn(r -> r instanceof Booking).hasSize(1);
        assertThat(results).filteredOn(r -> r instanceof EventFullException).hasSize(1);
        assertThat(bookingRepository.countByEventId(event.getId())).isEqualTo(2);
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getBookedCount()).isEqualTo(2);
    }

    @Test
    void manyParallelBookingsNeverExceedCapacity() throws Exception {
        int capacity = 3;
        Event event = newEvent(capacity);

        List<Callable<Object>> tasks = new ArrayList<>();
        for (long user = 1; user <= 12; user++) {
            long userId = user;
            tasks.add(() -> bookingService.book(event.getId(), userId));
        }
        List<Object> results = runInParallel(tasks.toArray(new Callable[0]));

        long successes = results.stream().filter(r -> r instanceof Booking).count();
        assertThat(successes).isLessThanOrEqualTo(capacity).isPositive();
        // every failure is a business conflict (HTTP 409), never an unexpected error
        assertThat(results).filteredOn(r -> !(r instanceof Booking))
                .allMatch(r -> r instanceof EventFullException || r instanceof ConcurrentBookingException);
        assertThat(bookingRepository.countByEventId(event.getId())).isEqualTo(successes);
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getBookedCount())
                .isEqualTo((int) successes);
    }

    @Test
    void staleEventVersionIsRejectedByOptimisticLock() {
        Event event = newEvent(5);
        Event stale = eventRepository.findById(event.getId()).orElseThrow(); // detached copy, old version

        bookingService.book(event.getId(), 1L); // bumps the version in the database

        stale.update("changed", null, stale.getStartsAt(), 5, stale.getOrganizerId());
        assertThatThrownBy(() -> eventRepository.save(stale))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void sameUserCannotBookTheSameEventTwice() {
        Event event = newEvent(5);
        bookingService.book(event.getId(), 1L);

        assertThatThrownBy(() -> bookingService.book(event.getId(), 1L))
                .isInstanceOf(AlreadyBookedException.class);
        assertThat(bookingRepository.countByEventId(event.getId())).isEqualTo(1);
    }

    @Test
    void databaseHasUniqueConstraintOnEventAndUser() {
        Event event = newEvent(5);
        String insert = "insert into bookings (event_id, user_id) values (?, ?)";
        jdbc.update(insert, event.getId(), 1L);

        assertThatThrownBy(() -> jdbc.update(insert, event.getId(), 1L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsBookedCountAboveCapacity() {
        Event event = newEvent(1);

        assertThatThrownBy(() -> jdbc.update("update events set booked_count = 2 where id = ?", event.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void fullEventReturns409ProblemDetailOverHttp() throws Exception {
        Event event = newEvent(1);
        String url = "/api/events/" + event.getId() + "/bookings";

        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"userId\": 1}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"userId\": 2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Event is full"));
    }

    private Event newEvent(int capacity) {
        return eventService.create(new EventRequest("Race event", null,
                Instant.now().plus(30, ChronoUnit.DAYS), capacity, 1L));
    }

    /** Starts all tasks at the same moment and returns each task's result or the exception it threw. */
    @SafeVarargs
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
