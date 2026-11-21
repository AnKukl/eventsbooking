package com.example.eventsbooking.booking;

import com.example.eventsbooking.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HOLD, confirmation, expiry, cancellation with refund and the Idempotency-Key against a real PostgreSQL and a clock
 * the test moves by hand.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true"
})
@AutoConfigureMockMvc
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class BookingFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    private static final Instant START = Instant.parse("2030-01-01T10:00:00Z");

    static class RecordingPaymentGateway implements PaymentGateway {
        final List<Long> refunded = new CopyOnWriteArrayList<>();

        @Override
        public void refund(Booking booking) {
            refunded.add(booking.getId());
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(START);
        }

        @Bean
        @Primary
        RecordingPaymentGateway recordingPaymentGateway() {
            return new RecordingPaymentGateway();
        }
    }

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final MutableClock clock;
    private final RecordingPaymentGateway gateway;
    private final HoldExpiryService expiry;
    private final BookingService bookingService;
    private final ObjectMapper json = new ObjectMapper();

    BookingFlowIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc, MutableClock clock,
            RecordingPaymentGateway gateway, HoldExpiryService expiry, BookingService bookingService) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.clock = clock;
        this.gateway = gateway;
        this.expiry = expiry;
        this.bookingService = bookingService;
    }

    /** Every test starts from an empty database (a throwaway test container) and a zero refund log. */
    @BeforeEach
    void reset() {
        clock.set(START);
        gateway.refunded.clear();
        jdbc.update("delete from events");   // cascades to bookings, idempotency keys, favorites, waitlist
    }

    // ---------- HOLD and expiry ----------

    @Test
    void aBookingIsAHoldThatTakesTheSeatsAndLastsTenMinutes() throws Exception {
        long event = event(10, Duration.ofDays(5));

        book(event, 2, 8001, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("HOLD"))
                .andExpect(jsonPath("$.seats").value(2))
                .andExpect(jsonPath("$.holdExpiresAt").value("2030-01-01T10:10:00Z"));

        assertThat(bookedCount(event)).isEqualTo(2);
    }

    @Test
    void anExpiredHoldGivesTheSeatBackAndSomeoneElseCanTakeIt() throws Exception {
        long event = event(1, Duration.ofDays(5));
        long hold = bookingId(book(event, 1, 8101, null));
        book(event, 1, 8102, null).andExpect(status().isConflict());

        clock.advance(Duration.ofMinutes(9).plusSeconds(59));
        assertThat(expiry.expireHolds()).isZero();
        assertThat(bookedCount(event)).isEqualTo(1);

        clock.advance(Duration.ofSeconds(1));           // exactly ten minutes after the booking
        assertThat(expiry.expireHolds()).isEqualTo(1);

        assertThat(statusOf(hold)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select cancellation_reason from bookings where id = ?", String.class, hold))
                .isEqualTo("HOLD_EXPIRED");
        assertThat(bookedCount(event)).isZero();
        book(event, 1, 8102, null).andExpect(status().isCreated());
    }

    @Test
    void confirmedBookingsNeverExpire() throws Exception {
        long event = event(5, Duration.ofDays(5));
        long booking = bookingId(book(event, 1, 8201, null));
        confirm(booking, 8201).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));

        clock.advance(Duration.ofHours(1));

        assertThat(expiry.expireHolds()).isZero();
        assertThat(statusOf(booking)).isEqualTo("CONFIRMED");
        assertThat(bookedCount(event)).isEqualTo(1);
    }

    @Test
    void aHoldCannotBeConfirmedOnceItsTimeIsUp() throws Exception {
        long event = event(5, Duration.ofDays(5));
        long booking = bookingId(book(event, 1, 8301, null));

        clock.advance(Duration.ofMinutes(10));

        confirm(booking, 8301).andExpect(status().isConflict());
        assertThat(expiry.expireHolds()).isEqualTo(1);
    }

    @Test
    void parallelExpiryRunsNeverReleaseASeatTwice() throws Exception {
        long event = event(100, Duration.ofDays(5));
        for (long user = 8401; user <= 8420; user++) {
            bookingService.book(event, user);
        }
        assertThat(bookedCount(event)).isEqualTo(20);
        clock.advance(Duration.ofMinutes(11));

        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> runs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            runs.add(pool.submit(() -> {
                go.await();
                return expiry.expireHolds();
            }));
        }
        go.countDown();
        int released = 0;
        for (Future<Integer> run : runs) {
            try {
                released += run.get();
            } catch (java.util.concurrent.ExecutionException e) {
                // a run that lost the race for the event's version is rolled back as a whole and retried below
                assertThat(e.getCause()).isInstanceOf(OptimisticLockingFailureException.class);
            }
        }
        pool.shutdown();
        for (int attempt = 0; attempt < 10 && released < 20; attempt++) {
            released += expiry.expireHolds();
        }

        assertThat(released).as("every hold is released exactly once").isEqualTo(20);
        assertThat(jdbc.queryForObject("select count(*) from bookings where event_id = ? and status = 'CANCELLED' "
                + "and cancellation_reason = 'HOLD_EXPIRED'", Long.class, event)).isEqualTo(20);
        assertThat(bookedCount(event)).isZero();
    }

    // ---------- cancellation and the 24-hour rule ----------

    @Test
    void cancellingExactly24HoursBeforeTheStartStartsARefund() throws Exception {
        long event = event(10, Duration.ofHours(30));
        long booking = confirmedBooking(event, 8501);
        clock.advance(Duration.ofHours(6));             // 24 hours to go, exactly

        cancel(booking, 8501, "CHANGED_PLANS").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUND_PENDING"))
                .andExpect(jsonPath("$.cancellationReason").value("CHANGED_PLANS"));

        assertThat(gateway.refunded).containsExactly(booking);
        assertThat(bookedCount(event)).isZero();
    }

    @Test
    void cancellingOneSecondLaterIsAllowedButThereIsNoRefund() throws Exception {
        long event = event(10, Duration.ofHours(30));
        long booking = confirmedBooking(event, 8601);
        clock.advance(Duration.ofHours(6).plusSeconds(1));

        cancel(booking, 8601, "OTHER").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(gateway.refunded).doesNotContain(booking);
        assertThat(bookedCount(event)).isZero();
    }

    @Test
    void aHoldCanBeCancelledForFreeEvenRightBeforeTheEvent() throws Exception {
        long event = event(10, Duration.ofHours(1));
        long hold = bookingId(book(event, 1, 8701, null));

        cancel(hold, 8701, "CHANGED_PLANS").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(gateway.refunded).doesNotContain(hold);
        assertThat(bookedCount(event)).isZero();
    }

    @Test
    void cancellingTwiceIsAConflictAndSomeoneElsesBookingIsNotFound() throws Exception {
        long event = event(10, Duration.ofDays(5));
        long booking = confirmedBooking(event, 8801);

        cancel(booking, 8802, "OTHER").andExpect(status().isNotFound());
        cancel(booking, 8801, "OTHER").andExpect(status().isOk());
        cancel(booking, 8801, "OTHER").andExpect(status().isConflict());

        assertThat(gateway.refunded).containsExactly(booking);
        assertThat(bookedCount(event)).isZero();
    }

    @Test
    void cancellingABookingReleasesAllItsSeats() throws Exception {
        long event = event(10, Duration.ofDays(5));
        long booking = bookingId(book(event, 4, 8901, null));
        assertThat(bookedCount(event)).isEqualTo(4);

        cancel(booking, 8901, "OTHER").andExpect(status().isOk());

        assertThat(bookedCount(event)).isZero();
    }

    // ---------- the seat limit ----------

    @Test
    void theServerLimitsSeatsPerBooking() throws Exception {
        long event = event(20, Duration.ofDays(5));

        book(event, 5, 9001, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid seat count"));
        book(event, 4, 9001, null).andExpect(status().isCreated());

        assertThat(bookedCount(event)).isEqualTo(4);
    }

    // ---------- Idempotency-Key ----------

    @Test
    void aRepeatedKeyReturnsTheSameBookingWithoutASecondRowOrSeat() throws Exception {
        long event = event(10, Duration.ofDays(5));

        long first = bookingId(book(event, 2, 9101, "key-aaa").andExpect(status().isCreated()));
        ResultActions again = book(event, 2, 9101, "key-aaa").andExpect(status().isOk());
        long second = bookingId(again);

        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from bookings where event_id = ? and user_id = 9101",
                Long.class, event)).isEqualTo(1);
        assertThat(bookedCount(event)).isEqualTo(2);
    }

    @Test
    void theKeyBelongsToTheUser() throws Exception {
        long event = event(10, Duration.ofDays(5));

        long mine = bookingId(book(event, 1, 9201, "shared-key"));
        long theirs = bookingId(book(event, 1, 9202, "shared-key").andExpect(status().isCreated()));

        assertThat(theirs).isNotEqualTo(mine);
    }

    @Test
    void theSameKeyForAnotherRequestIsAConflict() throws Exception {
        long event = event(10, Duration.ofDays(5));
        long other = event(10, Duration.ofDays(5));
        book(event, 2, 9301, "key-bbb").andExpect(status().isCreated());

        book(event, 3, 9301, "key-bbb").andExpect(status().isConflict());
        book(other, 2, 9301, "key-bbb").andExpect(status().isConflict());

        assertThat(bookedCount(event)).isEqualTo(2);
        assertThat(bookedCount(other)).isZero();
    }

    @Test
    void withoutAKeyASecondRequestIsAnAlreadyBookedConflict() throws Exception {
        long event = event(10, Duration.ofDays(5));
        book(event, 1, 9401, null).andExpect(status().isCreated());

        book(event, 1, 9401, null).andExpect(status().isConflict());
    }

    @Test
    void aFailedRequestDoesNotUseUpItsKey() throws Exception {
        long full = event(1, Duration.ofDays(5));
        book(full, 1, 9501, null).andExpect(status().isCreated());

        book(full, 1, 9502, "retry-key").andExpect(status().isConflict());
        jdbc.update("update events set capacity = 3 where id = ?", full);   // room appears
        book(full, 1, 9502, "retry-key").andExpect(status().isCreated());
    }

    @Test
    void parallelRequestsWithTheSameKeyCreateOneBooking() throws Exception {
        long event = event(10, Duration.ofDays(5));
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> statuses = new ArrayList<>();
        List<Long> ids = new CopyOnWriteArrayList<>();
        for (int i = 0; i < 4; i++) {
            statuses.add(pool.submit(() -> {
                go.await();
                ResultActions result = book(event, 1, 9601, "parallel-key");
                ids.add(bookingId(result));
                return result.andReturn().getResponse().getStatus();
            }));
        }
        go.countDown();
        List<Integer> codes = new ArrayList<>();
        for (Future<Integer> status : statuses) {
            codes.add(status.get());
        }
        pool.shutdown();

        assertThat(codes).containsOnly(200, 201);
        assertThat(codes.stream().filter(code -> code == 201)).hasSize(1);
        assertThat(ids.stream().distinct()).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from bookings where event_id = ?", Long.class, event))
                .isEqualTo(1);
        assertThat(bookedCount(event)).isEqualTo(1);
    }

    @Test
    void theDatabaseGuaranteesOneBookingPerKeyAndUser() throws Exception {
        long event = event(10, Duration.ofDays(5));
        book(event, 1, 9701, "db-key").andExpect(status().isCreated());

        assertThatThrownBy(() -> jdbc.update("insert into idempotency_keys (user_id, idempotency_key, event_id, seats, "
                + "created_at) values (9701, 'db-key', ?, 1, now())", event))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- helpers ----------

    private long event(int capacity, Duration startsIn) {
        return jdbc.queryForObject("insert into events (title, starts_at, capacity, organizer_id) "
                + "values ('Flow event', ?, ?, 1) returning id", Long.class,
                Timestamp.from(clock.instant().plus(startsIn)), capacity);
    }

    private long confirmedBooking(long event, long userId) throws Exception {
        long booking = bookingId(book(event, 1, userId, null));
        confirm(booking, userId).andExpect(status().isOk());
        return booking;
    }

    private ResultActions book(long event, int seats, long userId, String key) throws Exception {
        var request = post("/api/bookings").with(asUser(userId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":" + event + ",\"seats\":" + seats + "}");
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request);
    }

    private ResultActions confirm(long booking, long userId) throws Exception {
        return mockMvc.perform(post("/api/bookings/" + booking + "/confirm").with(asUser(userId)));
    }

    private ResultActions cancel(long booking, long userId, String reason) throws Exception {
        return mockMvc.perform(post("/api/bookings/" + booking + "/cancel").with(asUser(userId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + reason + "\"}"));
    }

    private long bookingId(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private int bookedCount(long event) {
        return jdbc.queryForObject("select booked_count from events where id = ?", Integer.class, event);
    }

    private String statusOf(long booking) {
        return jdbc.queryForObject("select status from bookings where id = ?", String.class, booking);
    }

    private static RequestPostProcessor asUser(long id) {
        return user(Long.toString(id)).roles("USER");
    }
}
