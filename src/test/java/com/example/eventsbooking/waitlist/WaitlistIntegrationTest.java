package com.example.eventsbooking.waitlist;

import com.example.eventsbooking.event.SeatReleased;
import com.example.eventsbooking.notification.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The waitlist end to end on a real PostgreSQL: queue order, one notification per seat, after-commit delivery. */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true"
})
@AutoConfigureMockMvc
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class WaitlistIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    private static final String FUTURE = Instant.now().plus(30, ChronoUnit.DAYS).toString();
    private static final String SEAT_AVAILABLE = "SEAT_AVAILABLE";

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher publisher;
    private final TransactionTemplate transaction;
    private final NotificationRepository notifications;
    private final ObjectMapper json = new ObjectMapper();

    WaitlistIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc, ApplicationEventPublisher publisher,
            PlatformTransactionManager transactionManager, NotificationRepository notifications) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.publisher = publisher;
        this.transaction = new TransactionTemplate(transactionManager);
        this.notifications = notifications;
    }

    // ---------- joining ----------

    @Test
    void joiningTwiceCreatesOneEntry() throws Exception {
        long event = soldOutEvent(1, 8001);

        mockMvc.perform(joinRequest(event, 8101, "EMAIL")).andExpect(status().isCreated());
        mockMvc.perform(joinRequest(event, 8101, "PUSH")).andExpect(status().isOk());
        mockMvc.perform(joinRequest(event, 8101, "EMAIL")).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select count(*) from waitlist where event_id = ? and user_id = 8101",
                Long.class, event)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select channel from waitlist where event_id = ? and user_id = 8101",
                String.class, event)).as("the first channel is kept").isEqualTo("EMAIL");
    }

    @Test
    void anEventWithFreeSeatsCannotBeWaitedFor() throws Exception {
        long event = createEvent(5);

        mockMvc.perform(joinRequest(event, 8102, "EMAIL"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void someoneWhoAlreadyBookedCannotJoin() throws Exception {
        long event = soldOutEvent(1, 8003);

        mockMvc.perform(joinRequest(event, 8003, "EMAIL")).andExpect(status().isConflict());
    }

    @Test
    void aStartedEventCannotBeWaitedFor() throws Exception {
        jdbc.update("insert into events (title, starts_at, capacity, organizer_id, booked_count) "
                + "values ('Started', now() - interval '1 hour', 1, 1, 1)");
        long event = jdbc.queryForObject("select max(id) from events", Long.class);

        mockMvc.perform(joinRequest(event, 8104, "EMAIL")).andExpect(status().isConflict());
    }

    @Test
    void unknownEventIs404() throws Exception {
        mockMvc.perform(joinRequest(987654, 8105, "EMAIL")).andExpect(status().isNotFound());
    }

    @Test
    void theDatabaseBacksTheRules() throws Exception {
        long event = soldOutEvent(1, 8006);
        jdbc.update("insert into waitlist (user_id, event_id, channel, created_at) values (8106, ?, 'EMAIL', now())", event);

        assertThatThrownBy(() -> jdbc.update(
                "insert into waitlist (user_id, event_id, channel, created_at) values (8106, ?, 'PUSH', now())", event))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "insert into waitlist (user_id, event_id, channel, created_at) values (8107, ?, 'SMS', now())", event))
                .isInstanceOf(DataIntegrityViolationException.class);

        mockMvc.perform(delete("/api/events/" + event).with(organizer())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from waitlist where event_id = ?", Long.class, event)).isZero();
    }

    // ---------- the queue ----------

    @Test
    void twoCancellationsInARowNotifyTheQueueInOrder() throws Exception {
        long event = createEvent(2);
        long firstBooking = book(event, 8201);
        long secondBooking = book(event, 8202);
        mockMvc.perform(joinRequest(event, 8211, "EMAIL")).andExpect(status().isCreated());
        mockMvc.perform(joinRequest(event, 8212, "PUSH")).andExpect(status().isCreated());
        mockMvc.perform(joinRequest(event, 8213, "EMAIL")).andExpect(status().isCreated());

        cancel(event, firstBooking, 8201);

        assertThat(seatNotifications(8211)).isEqualTo(1);
        assertThat(seatNotifications(8212)).isZero();
        assertThat(seatNotifications(8213)).isZero();
        assertThat(notifiedAt(event, 8211)).isNotNull();
        assertThat(notifiedAt(event, 8212)).isNull();

        cancel(event, secondBooking, 8202);

        assertThat(seatNotifications(8211)).as("not notified again").isEqualTo(1);
        assertThat(seatNotifications(8212)).isEqualTo(1);
        assertThat(seatNotifications(8213)).as("only one person per freed seat").isZero();
    }

    @Test
    void theNotificationTellsWhichEventHasASeat() throws Exception {
        long event = soldOutEvent(1, 8301);
        mockMvc.perform(joinRequest(event, 8311, "EMAIL")).andExpect(status().isCreated());

        cancel(event, bookingOf(event, 8301), 8301);

        mockMvc.perform(get("/api/me/notifications").with(user("8311").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value(SEAT_AVAILABLE))
                .andExpect(jsonPath("$.content[0].targetEventId").value(event))
                .andExpect(jsonPath("$.content[0].title").isNotEmpty())
                .andExpect(jsonPath("$.content[0].body").value(org.hamcrest.Matchers.containsString("Waitlist event")));
    }

    @Test
    void aDuplicateReleaseEventDoesNotNotifyAnyoneTwiceNorTheNextPerson() throws Exception {
        long event = soldOutEvent(1, 8401);
        mockMvc.perform(joinRequest(event, 8411, "EMAIL")).andExpect(status().isCreated());
        mockMvc.perform(joinRequest(event, 8412, "EMAIL")).andExpect(status().isCreated());
        UUID release = UUID.randomUUID();

        transaction.executeWithoutResult(status -> {
            publisher.publishEvent(new SeatReleased(event, release, 1));
            publisher.publishEvent(new SeatReleased(event, release, 1));   // the same event delivered twice
        });

        assertThat(seatNotifications(8411)).isEqualTo(1);
        assertThat(seatNotifications(8412)).as("the duplicate must not move on to the next person").isZero();
        assertThat(notifiedAt(event, 8412)).isNull();
    }

    @Test
    void aNewReleaseWithNobodyLeftInTheQueueCreatesNoSecondNotificationForTheSameUser() throws Exception {
        long event = soldOutEvent(1, 8501);
        mockMvc.perform(joinRequest(event, 8511, "EMAIL")).andExpect(status().isCreated());

        transaction.executeWithoutResult(status -> {
            publisher.publishEvent(new SeatReleased(event, UUID.randomUUID(), 1));
            publisher.publishEvent(new SeatReleased(event, UUID.randomUUID(), 1));
        });

        assertThat(seatNotifications(8511)).isEqualTo(1);
    }

    @Test
    void raisingCapacityNotifiesOnePersonPerAddedSeat() throws Exception {
        long event = soldOutEvent(1, 8601);
        for (int user = 8611; user <= 8614; user++) {
            mockMvc.perform(joinRequest(event, user, "EMAIL")).andExpect(status().isCreated());
        }

        mockMvc.perform(put("/api/events/" + event).with(organizer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Waitlist event\",\"startsAt\":\"" + FUTURE + "\",\"capacity\":3}"))
                .andExpect(status().isOk());

        assertThat(seatNotifications(8611)).isEqualTo(1);
        assertThat(seatNotifications(8612)).isEqualTo(1);
        assertThat(seatNotifications(8613)).isZero();
        assertThat(seatNotifications(8614)).isZero();
    }

    @Test
    void peopleWhoAlreadyHoldASeatAreSkipped() throws Exception {
        long event = createEvent(5);
        book(event, 8701);
        // queue rows inserted directly: 8701 is first in line although it already has a booking
        jdbc.update("insert into waitlist (user_id, event_id, channel, created_at) values (8701, ?, 'EMAIL', now())", event);
        jdbc.update("insert into waitlist (user_id, event_id, channel, created_at) values (8702, ?, 'EMAIL', now())", event);

        transaction.executeWithoutResult(status ->
                publisher.publishEvent(new SeatReleased(event, UUID.randomUUID(), 1)));

        assertThat(seatNotifications(8701)).isZero();
        assertThat(seatNotifications(8702)).isEqualTo(1);
    }

    // ---------- after commit only ----------

    @Test
    void nothingIsDeliveredBeforeTheCommit() throws Exception {
        long event = soldOutEvent(1, 8801);
        mockMvc.perform(joinRequest(event, 8811, "EMAIL")).andExpect(status().isCreated());

        transaction.executeWithoutResult(status -> {
            publisher.publishEvent(new SeatReleased(event, UUID.randomUUID(), 1));
            assertThat(seatNotifications(8811)).as("still inside the transaction").isZero();
            assertThat(notifiedAt(event, 8811)).isNull();
        });

        assertThat(seatNotifications(8811)).as("delivered once the transaction committed").isEqualTo(1);
    }

    @Test
    void aRolledBackTransactionDeliversNothing() throws Exception {
        long event = soldOutEvent(1, 8901);
        mockMvc.perform(joinRequest(event, 8911, "EMAIL")).andExpect(status().isCreated());

        transaction.executeWithoutResult(status -> {
            publisher.publishEvent(new SeatReleased(event, UUID.randomUUID(), 1));
            status.setRollbackOnly();
        });

        assertThat(seatNotifications(8911)).isZero();
        assertThat(notifiedAt(event, 8911)).isNull();
    }

    // ---------- the feed ----------

    @Test
    void theFeedIsPaginatedNewestFirstAndPrivate() throws Exception {
        for (int i = 0; i < 5; i++) {
            notifications.insertSeatAvailable(UUID.randomUUID(), 1L, 9001L, "Title " + i, "Body " + i, Instant.parse("2030-01-01T10:00:00Z").plusSeconds(60L * i));
        }
        notifications.insertSeatAvailable(UUID.randomUUID(), 1L, 9002L, "Not yours", "x",
                Instant.parse("2030-01-01T10:00:00Z"));

        mockMvc.perform(get("/api/me/notifications").param("page", "0").param("size", "2")
                        .with(user("9001").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].title").value("Title 4"))
                .andExpect(jsonPath("$.content[1].title").value("Title 3"))
                .andExpect(jsonPath("$.page.totalElements").value(5))
                .andExpect(jsonPath("$.page.totalPages").value(3));
        mockMvc.perform(get("/api/me/notifications").param("page", "2").param("size", "2")
                        .with(user("9001").roles("USER")))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Title 0"));
        mockMvc.perform(get("/api/me/notifications").with(user("9003").roles("USER")))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    // ---------- helpers ----------

    /** An event with the given capacity, filled by users starting at {@code firstUser}. */
    private long soldOutEvent(int capacity, int firstUser) throws Exception {
        long event = createEvent(capacity);
        for (int i = 0; i < capacity; i++) {
            book(event, firstUser + i);
        }
        return event;
    }

    private long createEvent(int capacity) throws Exception {
        String body = mockMvc.perform(post("/api/events").with(organizer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Waitlist event\",\"startsAt\":\"" + FUTURE + "\",\"capacity\":" + capacity + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private long book(long event, long userId) throws Exception {
        String body = mockMvc.perform(post("/api/events/" + event + "/bookings").with(asUser(userId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private long bookingOf(long event, long userId) {
        return jdbc.queryForObject("select id from bookings where event_id = ? and user_id = ?", Long.class, event, userId);
    }

    private void cancel(long event, long booking, long userId) throws Exception {
        mockMvc.perform(post("/api/events/" + event + "/bookings/" + booking + "/cancel").with(asUser(userId)))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder joinRequest(
            long event, long userId, String channel) {
        return post("/api/events/" + event + "/waitlist").with(asUser(userId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"channel\":\"" + channel + "\"}");
    }

    private long seatNotifications(long userId) {
        return jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = ?",
                Long.class, userId, SEAT_AVAILABLE);
    }

    private java.sql.Timestamp notifiedAt(long event, long userId) {
        return jdbc.queryForObject("select notified_at from waitlist where event_id = ? and user_id = ?",
                java.sql.Timestamp.class, event, userId);
    }

    private static RequestPostProcessor asUser(long id) {
        return user(Long.toString(id)).roles("USER");
    }

    private static RequestPostProcessor organizer() {
        return user("1").roles("ORGANIZER");
    }
}
