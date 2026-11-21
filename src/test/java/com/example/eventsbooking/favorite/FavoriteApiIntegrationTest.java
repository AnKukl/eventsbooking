package com.example.eventsbooking.favorite;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Favorites against a real PostgreSQL: idempotency, per-user isolation, pagination, isFavorite, cascade. */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true"
})
@AutoConfigureMockMvc
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FavoriteApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    private static final String FUTURE = Instant.now().plus(30, ChronoUnit.DAYS).toString();

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    FavoriteApiIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    // ---------- idempotency ----------

    @Test
    void repeatedPostCreatesNoDuplicate() throws Exception {
        long event = createEvent(10);

        mockMvc.perform(post("/api/favorites/" + event).with(asUser(201))).andExpect(status().isCreated());
        mockMvc.perform(post("/api/favorites/" + event).with(asUser(201))).andExpect(status().isOk());
        mockMvc.perform(post("/api/favorites/" + event).with(asUser(201))).andExpect(status().isOk());

        assertThat(rows(201, event)).isEqualTo(1);
    }

    @Test
    void deleteOfAMissingFavoriteIs204AndDeleteIsRepeatable() throws Exception {
        long event = createEvent(10);

        mockMvc.perform(delete("/api/favorites/" + event).with(asUser(202))).andExpect(status().isNoContent());

        mockMvc.perform(post("/api/favorites/" + event).with(asUser(202))).andExpect(status().isCreated());
        mockMvc.perform(delete("/api/favorites/" + event).with(asUser(202))).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/favorites/" + event).with(asUser(202))).andExpect(status().isNoContent());

        assertThat(rows(202, event)).isZero();
    }

    @Test
    void deleteOfAnUnknownEventIs204() throws Exception {
        mockMvc.perform(delete("/api/favorites/987654").with(asUser(203))).andExpect(status().isNoContent());
    }

    @Test
    void postForAnUnknownEventIs404AndStoresNothing() throws Exception {
        mockMvc.perform(post("/api/favorites/987654").with(asUser(204)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        assertThat(jdbc.queryForObject("select count(*) from favorites where user_id = 204", Long.class)).isZero();
    }

    @Test
    void deletingOneUsersFavoriteLeavesTheOthersIntact() throws Exception {
        long event = createEvent(10);
        mockMvc.perform(post("/api/favorites/" + event).with(asUser(205))).andExpect(status().isCreated());
        mockMvc.perform(post("/api/favorites/" + event).with(asUser(206))).andExpect(status().isCreated());

        mockMvc.perform(delete("/api/favorites/" + event).with(asUser(205))).andExpect(status().isNoContent());

        assertThat(rows(205, event)).isZero();
        assertThat(rows(206, event)).isEqualTo(1);
    }

    // ---------- isolation and pagination ----------

    @Test
    void anotherUsersFavoritesAreNotVisible() throws Exception {
        long mine = createEvent(10);
        long theirs = createEvent(10);
        mockMvc.perform(post("/api/favorites/" + mine).with(asUser(211))).andExpect(status().isCreated());
        mockMvc.perform(post("/api/favorites/" + theirs).with(asUser(212))).andExpect(status().isCreated());

        mockMvc.perform(get("/api/favorites").with(asUser(211)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(mine));
        mockMvc.perform(get("/api/favorites").with(asUser(213)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void listIsPaginatedNewestFirst() throws Exception {
        long[] events = new long[5];
        for (int i = 0; i < events.length; i++) {
            events[i] = createEvent(10);
            mockMvc.perform(post("/api/favorites/" + events[i]).with(asUser(221))).andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/favorites").param("page", "0").param("size", "2").with(asUser(221)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(events[4]))
                .andExpect(jsonPath("$.content[1].id").value(events[3]))
                .andExpect(jsonPath("$.page.totalElements").value(5))
                .andExpect(jsonPath("$.page.totalPages").value(3));
        mockMvc.perform(get("/api/favorites").param("page", "2").param("size", "2").with(asUser(221)))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(events[0]));
    }

    @Test
    void removedFavoriteDisappearsFromTheList() throws Exception {
        long event = createEvent(10);
        mockMvc.perform(post("/api/favorites/" + event).with(asUser(231))).andExpect(status().isCreated());
        mockMvc.perform(delete("/api/favorites/" + event).with(asUser(231))).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/favorites").with(asUser(231)))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    // ---------- seat status ----------

    @Test
    void favoritesShowSeatStatusOfEachEvent() throws Exception {
        long scarce = createEvent(1);
        long roomy = createEvent(10);
        mockMvc.perform(post("/api/events/" + scarce + "/bookings").with(asUser(242))).andExpect(status().isCreated());
        mockMvc.perform(post("/api/favorites/" + scarce).with(asUser(241))).andExpect(status().isCreated());
        mockMvc.perform(post("/api/favorites/" + roomy).with(asUser(241))).andExpect(status().isCreated());

        mockMvc.perform(get("/api/favorites").with(asUser(241)))
                .andExpect(jsonPath("$.content[0].id").value(roomy))
                .andExpect(jsonPath("$.content[0].seatStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.content[1].id").value(scarce))
                .andExpect(jsonPath("$.content[1].seatStatus").value("SOLD_OUT"));
    }

    // ---------- isFavorite ----------

    @Test
    void isFavoriteIsTrueOnlyForTheUserWhoFavoritedAndFalseForGuests() throws Exception {
        long liked = createEvent(10);
        long other = createEvent(10);
        mockMvc.perform(post("/api/favorites/" + liked).with(asUser(251))).andExpect(status().isCreated());

        // card
        mockMvc.perform(get("/api/events/" + liked).with(asUser(251))).andExpect(jsonPath("$.isFavorite").value(true));
        mockMvc.perform(get("/api/events/" + other).with(asUser(251))).andExpect(jsonPath("$.isFavorite").value(false));
        mockMvc.perform(get("/api/events/" + liked).with(asUser(252))).andExpect(jsonPath("$.isFavorite").value(false));
        mockMvc.perform(get("/api/events/" + liked).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isFavorite").value(false));

        // list
        assertThat(isFavoriteInList(251, liked)).isTrue();
        assertThat(isFavoriteInList(251, other)).isFalse();
        assertThat(isFavoriteInList(252, liked)).isFalse();
        assertThat(isFavoriteInList(null, liked)).isFalse();
    }

    // ---------- guests and cascade ----------

    @Test
    void guestsCannotUseFavoritesEndpoints() throws Exception {
        mockMvc.perform(post("/api/favorites/1").with(anonymous())).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/favorites/1").with(anonymous())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/favorites").with(anonymous())).andExpect(status().isUnauthorized());
    }

    @Test
    void deletingAnEventRemovesItsFavorites() throws Exception {
        long event = createEvent(10);
        mockMvc.perform(post("/api/favorites/" + event).with(asUser(261))).andExpect(status().isCreated());

        mockMvc.perform(delete("/api/events/" + event).with(organizer())).andExpect(status().isNoContent());

        assertThat(rows(261, event)).isZero();
        mockMvc.perform(get("/api/favorites").with(asUser(261))).andExpect(jsonPath("$.page.totalElements").value(0));
    }

    // ---------- helpers ----------

    private boolean isFavoriteInList(Integer userId, long eventId) throws Exception {
        var request = get("/api/events").param("size", "100").param("sort", "id,desc");
        request.with(userId == null ? anonymous() : asUser(userId));
        String body = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode item : json.readTree(body).get("content")) {
            if (item.get("id").asLong() == eventId) {
                return item.get("isFavorite").asBoolean();
            }
        }
        throw new AssertionError("event " + eventId + " not in the list");
    }

    private long createEvent(int capacity) throws Exception {
        String body = mockMvc.perform(post("/api/events").with(organizer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Fav event\",\"startsAt\":\"" + FUTURE + "\",\"capacity\":" + capacity + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private long rows(long userId, long eventId) {
        return jdbc.queryForObject("select count(*) from favorites where user_id = ? and event_id = ?",
                Long.class, userId, eventId);
    }

    private static RequestPostProcessor asUser(long id) {
        return user(Long.toString(id)).roles("USER");
    }

    private static RequestPostProcessor organizer() {
        return user("1").roles("ORGANIZER");
    }
}
