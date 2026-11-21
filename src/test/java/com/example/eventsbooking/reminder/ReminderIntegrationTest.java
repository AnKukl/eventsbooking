package com.example.eventsbooking.reminder;

import com.example.eventsbooking.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Reminders against a real PostgreSQL with a clock the test controls. */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true"
})
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ReminderIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    private static final Instant START = Instant.parse("2030-01-01T10:00:00Z");

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(START);
        }
    }

    private final ReminderService service;
    private final JdbcTemplate jdbc;
    private final MutableClock clock;

    ReminderIntegrationTest(ReminderService service, JdbcTemplate jdbc, MutableClock clock) {
        this.service = service;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @BeforeEach
    void resetClock() {
        clock.set(START);
    }

    @Test
    void remindsEveryoneWithABookingOrAFavoriteExactlyOnce() {
        long event = event("Jazz night", START.plus(Duration.ofHours(23)));
        booking(event, 1001, "CONFIRMED");
        favorite(event, 1002);
        booking(event, 1003, "CONFIRMED");
        favorite(event, 1003);                   // both: still one reminder
        booking(event, 1005, "CANCELLED");       // cancelled: no reminder
        // 1004 has nothing to do with the event

        service.sendDueReminders();

        assertThat(reminders(1001, event, "REMINDER_24H")).isEqualTo(1);
        assertThat(reminders(1002, event, "REMINDER_24H")).isEqualTo(1);
        assertThat(reminders(1003, event, "REMINDER_24H")).isEqualTo(1);
        assertThat(reminders(1004, event, "REMINDER_24H")).isZero();
        assertThat(reminders(1005, event, "REMINDER_24H")).isZero();
    }

    @Test
    void runningTheTaskTwiceCreatesNoDuplicates() {
        long event = event("Twice", START.plus(Duration.ofHours(20)));
        booking(event, 1101, "CONFIRMED");
        favorite(event, 1102);

        service.sendDueReminders();
        service.sendDueReminders();
        clock.advance(Duration.ofMinutes(5));
        service.sendDueReminders();

        assertThat(reminders(1101, event, "REMINDER_24H")).isEqualTo(1);
        assertThat(reminders(1102, event, "REMINDER_24H")).isEqualTo(1);
        assertThat(sentAt(event, "reminder_24h_sent_at")).isEqualTo(Timestamp.from(START));
    }

    @Test
    void twoInstancesRunningAtTheSameTimeStillRemindOnce() throws Exception {
        long event = event("Two nodes", START.plus(Duration.ofHours(10)));
        for (int user = 1201; user <= 1205; user++) {
            booking(event, user, "CONFIRMED");
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> runs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            runs.add(pool.submit(() -> {
                go.await();
                return service.sendDueReminders();
            }));
        }
        go.countDown();
        for (Future<?> run : runs) {
            run.get();
        }
        pool.shutdown();

        for (int user = 1201; user <= 1205; user++) {
            assertThat(reminders(user, event, "REMINDER_24H")).as("user " + user).isEqualTo(1);
        }
    }

    @Test
    void theTwoHourReminderIsSeparateFromTheDayBeforeOne() {
        long event = event("Both", START.plus(Duration.ofHours(23)));
        booking(event, 1301, "CONFIRMED");

        service.sendDueReminders();                   // 23 h before: the 24 h reminder
        clock.advance(Duration.ofMinutes(21 * 60 + 30));
        service.sendDueReminders();                   // 1.5 h before: the 2 h reminder
        service.sendDueReminders();

        assertThat(reminders(1301, event, "REMINDER_24H")).isEqualTo(1);
        assertThat(reminders(1301, event, "REMINDER_2H")).isEqualTo(1);
        assertThat(sentAt(event, "reminder_2h_sent_at")).isNotNull();
    }

    @Test
    void anEventCreatedLessThanTwoHoursAheadGetsOnlyTheTwoHourReminder() {
        long event = event("Last minute", START.plus(Duration.ofHours(1)));
        booking(event, 1401, "CONFIRMED");

        service.sendDueReminders();

        assertThat(reminders(1401, event, "REMINDER_2H")).isEqualTo(1);
        assertThat(reminders(1401, event, "REMINDER_24H")).isZero();
    }

    @Test
    void eventsOutsideTheWindowsAreLeftAloneUntilTheirTimeComes() {
        long far = event("Far away", START.plus(Duration.ofHours(30)));
        long past = event("Already over", START.minus(Duration.ofHours(1)));
        booking(far, 1501, "CONFIRMED");
        booking(past, 1501, "CONFIRMED");

        service.sendDueReminders();

        assertThat(reminders(1501, far, "REMINDER_24H")).isZero();
        assertThat(reminders(1501, past, "REMINDER_24H")).isZero();
        assertThat(reminders(1501, past, "REMINDER_2H")).isZero();
        assertThat(sentAt(far, "reminder_24h_sent_at")).isNull();

        clock.advance(Duration.ofHours(7));           // now the far event is 23 h away
        service.sendDueReminders();

        assertThat(reminders(1501, far, "REMINDER_24H")).isEqualTo(1);
    }

    @Test
    void theReminderTellsWhichEventItIsAbout() {
        long event = event("Opera gala", START.plus(Duration.ofHours(12)));
        favorite(event, 1601);

        service.sendDueReminders();

        var row = jdbc.queryForMap("select * from notifications where user_id = 1601 and target_event_id = ?", event);
        assertThat(row.get("type")).isEqualTo("REMINDER_24H");
        assertThat((String) row.get("body")).contains("Opera gala");
        assertThat((String) row.get("title")).isNotBlank();
        assertThat(((Timestamp) row.get("created_at")).toInstant()).isEqualTo(START);
    }

    // ---------- helpers ----------

    private long event(String title, Instant startsAt) {
        return jdbc.queryForObject("insert into events (title, starts_at, capacity, organizer_id) "
                + "values (?, ?, 100, 1) returning id", Long.class, title, Timestamp.from(startsAt));
    }

    private void booking(long event, long user, String status) {
        jdbc.update("insert into bookings (event_id, user_id, status) values (?, ?, ?)", event, user, status);
    }

    private void favorite(long event, long user) {
        jdbc.update("insert into favorites (user_id, event_id, created_at) values (?, ?, now())", user, event);
    }

    private long reminders(long user, long event, String type) {
        return jdbc.queryForObject("select count(*) from notifications where user_id = ? and target_event_id = ? "
                + "and type = ?", Long.class, user, event, type);
    }

    private Timestamp sentAt(long event, String column) {
        return jdbc.queryForObject("select " + column + " from events where id = ?", Timestamp.class, event);
    }
}
