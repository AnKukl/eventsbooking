package com.example.eventsbooking.notification;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** What the personal cabinet needs: unread notifications are counted and marked read; bookings come with their event. */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true"
})
@AutoConfigureMockMvc
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AccountApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    private static final AtomicInteger USERS = new AtomicInteger();
    private static final String ORIGIN = "http://localhost:5173";

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;

    AccountApiIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    @Test
    void unreadNotificationsAreCountedAndMarkedReadAllAtOnce() throws Exception {
        Session me = newUser();
        Session other = newUser();
        notification(me.id, null);
        notification(me.id, null);
        notification(me.id, "2030-01-01T10:00:00Z");
        notification(other.id, null);

        mockMvc.perform(get("/api/me/notifications/unread-count").cookie(me.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));

        mockMvc.perform(post("/api/me/notifications/read").cookie(me.cookie).header("Origin", ORIGIN))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me/notifications/unread-count").cookie(me.cookie)).andExpect(jsonPath("$.count").value(0));
        mockMvc.perform(get("/api/me/notifications").cookie(me.cookie))
                .andExpect(jsonPath("$.content[0].readAt").isNotEmpty());
        mockMvc.perform(get("/api/me/notifications/unread-count").cookie(other.cookie)).andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void onlyTheGivenNotificationsAreMarkedRead() throws Exception {
        Session me = newUser();
        long first = notification(me.id, null);
        notification(me.id, null);

        mockMvc.perform(post("/api/me/notifications/read").cookie(me.cookie).header("Origin", ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[" + first + "]}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me/notifications/unread-count").cookie(me.cookie)).andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void myBookingsComeWithTheirEvent() throws Exception {
        Session me = newUser();
        jdbc.update("insert into events (title, starts_at, capacity, organizer_id, venue, price) "
                + "values ('Свет на мраморе', now() + interval '2 days', 20, 1, 'Музей, Гданьск', 45)");
        long eventId = jdbc.queryForObject("select max(id) from events", Long.class);
        jdbc.update("insert into bookings (event_id, user_id, seats, status) values (?, ?, 2, 'CONFIRMED')", eventId, me.id);

        mockMvc.perform(get("/api/bookings/my").cookie(me.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].seats").value(2))
                .andExpect(jsonPath("$.content[0].event.title").value("Свет на мраморе"))
                .andExpect(jsonPath("$.content[0].event.venue").value("Музей, Гданьск"))
                .andExpect(jsonPath("$.content[0].event.price").value(45))
                .andExpect(jsonPath("$.content[0].event.startsAt").isNotEmpty());
    }

    record Session(long id, Cookie cookie) {
    }

    private Session newUser() throws Exception {
        String email = "account-" + USERS.incrementAndGet() + "@example.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse-battery\"}")).andExpect(status().isCreated());
        String token = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse-battery\"}"))
                .andReturn().getResponse().getCookie("access_token").getValue();
        long id = jdbc.queryForObject("select id from users where email = ?", Long.class, email);
        return new Session(id, new Cookie("access_token", token));
    }

    private long notification(long userId, String readAt) {
        jdbc.update("insert into notifications (event_id, user_id, type, title, body, read_at) "
                + "values (?, ?, 'SEAT_AVAILABLE', 'Освободилось место', 'Кухня Pomorza', ?::timestamptz)", UUID.randomUUID(), userId, readAt);
        return jdbc.queryForObject("select max(id) from notifications", Long.class);
    }
}
