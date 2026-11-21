package com.example.eventsbooking.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true"
})
@AutoConfigureMockMvc
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class NotificationPreferencesIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;

    NotificationPreferencesIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    @Test
    void withoutAnyChoiceEveryTypeIsOnForBothChannels() throws Exception {
        mockMvc.perform(get("/api/me/notifications/preferences").with(asUser(3001)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences.length()").value(NotificationType.values().length))
                .andExpect(jsonPath("$.preferences[?(@.email==false || @.push==false)]").isEmpty());
    }

    @Test
    void putStoresChoicesPerTypeAndGetReflectsThem() throws Exception {
        save(3002, "{\"preferences\":[{\"type\":\"SEAT_AVAILABLE\",\"email\":false,\"push\":true},"
                + "{\"type\":\"REMINDER_24H\",\"email\":true,\"push\":false}]}");

        mockMvc.perform(get("/api/me/notifications/preferences").with(asUser(3002)))
                .andExpect(jsonPath("$.preferences[?(@.type=='SEAT_AVAILABLE')].email").value(false))
                .andExpect(jsonPath("$.preferences[?(@.type=='SEAT_AVAILABLE')].push").value(true))
                .andExpect(jsonPath("$.preferences[?(@.type=='REMINDER_24H')].push").value(false))
                .andExpect(jsonPath("$.preferences[?(@.type=='BOOKING_CONFIRMED')].email").value(true));
    }

    @Test
    void savingTwiceUpdatesTheSameRow() throws Exception {
        save(3003, "{\"preferences\":[{\"type\":\"REMINDER_2H\",\"email\":false,\"push\":false}]}");
        save(3003, "{\"preferences\":[{\"type\":\"REMINDER_2H\",\"email\":true,\"push\":false}]}");

        assertThat(jdbc.queryForObject("select count(*) from notification_preferences where user_id = 3003",
                Long.class)).isEqualTo(1);
        mockMvc.perform(get("/api/me/notifications/preferences").with(asUser(3003)))
                .andExpect(jsonPath("$.preferences[?(@.type=='REMINDER_2H')].email").value(true));
    }

    @Test
    void choicesArePrivateToTheirOwner() throws Exception {
        save(3004, "{\"preferences\":[{\"type\":\"SEAT_AVAILABLE\",\"email\":false,\"push\":false}]}");

        mockMvc.perform(get("/api/me/notifications/preferences").with(asUser(3005)))
                .andExpect(jsonPath("$.preferences[?(@.type=='SEAT_AVAILABLE')].email").value(true));
    }

    @Test
    void theDatabaseRejectsAnUnknownType() {
        assertThatThrownBy(() -> jdbc.update("insert into notification_preferences (user_id, type, email, push) "
                + "values (3006, 'SPAM', true, true)")).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void save(long userId, String body) throws Exception {
        mockMvc.perform(put("/api/me/notifications/preferences").with(asUser(userId))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }

    private static RequestPostProcessor asUser(long id) {
        return user(Long.toString(id)).roles("USER");
    }
}
