package com.example.eventsbooking.event;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
@WithMockUser(username = "1", roles = "ORGANIZER")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class EventApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    private static final String FUTURE = Instant.now().plus(30, ChronoUnit.DAYS).toString();

    private final MockMvc mockMvc;

    EventApiIntegrationTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void fullCrudLifecycle() throws Exception {
        String location = mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Jazz night", 50)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Jazz night"))
                .andExpect(jsonPath("$.capacity").value(50));

        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Rock night", 80)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Rock night"))
                .andExpect(jsonPath("$.capacity").value(80));

        mockMvc.perform(delete(location)).andExpect(status().isNoContent());

        mockMvc.perform(get(location))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void listIsPaginated() throws Exception {
        for (int i = 0; i < 12; i++) {
            mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON)
                            .content(body("Paged event " + i, 10)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/events").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(12)));

        mockMvc.perform(get("/api/events").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    void onlyTheOrganizerWhoCreatedTheEventMayChangeOrDeleteIt() throws Exception {
        String location = mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Owned", 10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizerId").value(1))
                .andReturn().getResponse().getHeader("Location");

        var other = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user("2").roles("ORGANIZER");

        mockMvc.perform(put(location).with(other).contentType(MediaType.APPLICATION_JSON).content(body("Hijacked", 99)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(location).with(other)).andExpect(status().isForbidden());

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Owned"))
                .andExpect(jsonPath("$.capacity").value(10));
    }

    @Test
    void organizerIdFromTheRequestBodyIsIgnored() throws Exception {
        mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Spoof\",\"startsAt\":\"" + FUTURE + "\",\"capacity\":5,\"organizerId\":99}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizerId").value(1));
    }

    @Test
    void sortingByAnInternalFieldIsRejectedAndUnknownOnesDoNotCrash() throws Exception {
        mockMvc.perform(get("/api/events").param("sort", "foo")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/events").param("sort", "version")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/events").param("sort", "startsAt,desc")).andExpect(status().isOk());
    }

    private static String body(String title, int capacity) {
        return """
                {"title": "%s", "description": "d", "startsAt": "%s", "capacity": %d}
                """.formatted(title, FUTURE, capacity);
    }
}
