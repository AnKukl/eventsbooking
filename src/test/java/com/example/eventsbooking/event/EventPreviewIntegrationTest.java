package com.example.eventsbooking.event;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Preview fields against a real PostgreSQL: ordering, replacement, caching, constraints, cascade. */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true"
})
@AutoConfigureMockMvc
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class EventPreviewIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    private static final String FUTURE = Instant.now().plus(30, ChronoUnit.DAYS).toString();

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    EventPreviewIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    @Test
    void previewFieldsRoundTripInTheOrderTheyWereGiven() throws Exception {
        long id = create(preview("Pitch", "[\"first\",\"second\",\"third\"]", "[\"no1\",\"no2\"]",
                "[{\"url\":\"https://img.example/cover.png\",\"alt\":\"Cover\"},{\"url\":\"https://img.example/2.png\"}]"));

        mockMvc.perform(get("/api/events/" + id + "/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.shortDescription").value("Pitch"))
                .andExpect(jsonPath("$.audienceFit[0]").value("first"))
                .andExpect(jsonPath("$.audienceFit[1]").value("second"))
                .andExpect(jsonPath("$.audienceFit[2]").value("third"))
                .andExpect(jsonPath("$.audienceNotFit[1]").value("no2"))
                .andExpect(jsonPath("$.media[0].url").value("https://img.example/cover.png"))
                .andExpect(jsonPath("$.media[0].alt").value("Cover"))
                .andExpect(jsonPath("$.media[1].url").value("https://img.example/2.png"));
    }

    @Test
    void anEventWithoutPreviewDataHasAnEmptyPreview() throws Exception {
        long id = create("");

        mockMvc.perform(get("/api/events/" + id + "/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortDescription").value(""))
                .andExpect(jsonPath("$.audienceFit.length()").value(0))
                .andExpect(jsonPath("$.audienceNotFit.length()").value(0))
                .andExpect(jsonPath("$.media.length()").value(0));
    }

    @Test
    void updateReplacesListsAndShrinksThemInTheDatabase() throws Exception {
        long id = create(preview("Old", "[\"a\",\"b\",\"c\",\"d\"]", "[\"x\",\"y\",\"z\"]",
                "[{\"url\":\"https://img.example/1.png\"},{\"url\":\"https://img.example/2.png\"}]"));

        update(id, preview("New", "[\"only\"]", "[]", "[{\"url\":\"https://img.example/9.png\"}]"));

        mockMvc.perform(get("/api/events/" + id + "/preview"))
                .andExpect(jsonPath("$.shortDescription").value("New"))
                .andExpect(jsonPath("$.audienceFit.length()").value(1))
                .andExpect(jsonPath("$.audienceFit[0]").value("only"))
                .andExpect(jsonPath("$.audienceNotFit.length()").value(0))
                .andExpect(jsonPath("$.media.length()").value(1))
                .andExpect(jsonPath("$.media[0].url").value("https://img.example/9.png"));
        assertThat(count("event_audience_fit", id)).isEqualTo(1);
        assertThat(count("event_audience_not_fit", id)).isZero();
        assertThat(count("event_media", id)).isEqualTo(1);
    }

    @Test
    void updateWithoutPreviewFieldsKeepsThem() throws Exception {
        long id = create(preview("Keep me", "[\"a\"]", "[\"b\"]", "[{\"url\":\"https://img.example/1.png\"}]"));

        update(id, "");

        mockMvc.perform(get("/api/events/" + id + "/preview"))
                .andExpect(jsonPath("$.shortDescription").value("Keep me"))
                .andExpect(jsonPath("$.audienceFit[0]").value("a"))
                .andExpect(jsonPath("$.audienceNotFit[0]").value("b"))
                .andExpect(jsonPath("$.media.length()").value(1));
    }

    @Test
    void eTagRevalidationGives304UntilThePreviewChanges() throws Exception {
        long id = create(preview("Pitch", "[\"a\"]", "[]", "[]"));
        String url = "/api/events/" + id + "/preview";
        var first = mockMvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG)).andReturn().getResponse();
        String etag = first.getHeader(HttpHeaders.ETAG);
        assertThat(first.getHeader(HttpHeaders.CACHE_CONTROL)).contains("max-age=300");

        mockMvc.perform(get(url).header(HttpHeaders.IF_NONE_MATCH, etag)).andExpect(status().isNotModified());

        update(id, preview("Changed", "[\"a\"]", "[]", "[]"));

        var after = mockMvc.perform(get(url).header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(after.getHeader(HttpHeaders.ETAG)).isNotEqualTo(etag);
    }

    @Test
    void bookingsDoNotInvalidateThePreviewETag() throws Exception {
        long id = create(preview("Pitch", "[\"a\"]", "[]", "[]"));
        String url = "/api/events/" + id + "/preview";
        String etag = mockMvc.perform(get(url)).andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        mockMvc.perform(post("/api/events/" + id + "/bookings").with(user("501").roles("USER")))
                .andExpect(status().isCreated());

        mockMvc.perform(get(url).header(HttpHeaders.IF_NONE_MATCH, etag)).andExpect(status().isNotModified());
    }

    @Test
    void guestsCanReadThePreviewAndUnknownEventsAre404() throws Exception {
        long id = create("");

        mockMvc.perform(get("/api/events/" + id + "/preview").with(anonymous())).andExpect(status().isOk());
        mockMvc.perform(get("/api/events/987654/preview").with(anonymous()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void overTheLimitIsRejectedAndStoresNothing() throws Exception {
        long before = jdbc.queryForObject("select count(*) from events where title = 'Rejected preview'", Long.class);

        mockMvc.perform(post("/api/events").with(organizer()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Rejected preview", preview("x", "[\"1\",\"2\",\"3\",\"4\",\"5\"]", "[]", "[]"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='audienceFit')]").exists());

        assertThat(jdbc.queryForObject("select count(*) from events where title = 'Rejected preview'", Long.class))
                .isEqualTo(before);
    }

    @Test
    void databaseConstraintsBackTheLimitsToo() throws Exception {
        long id = create("");

        assertThatThrownBy(() -> jdbc.update(
                "insert into event_audience_fit (event_id, position, label) values (?, 4, 'fifth')", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "insert into event_audience_not_fit (event_id, position, label) values (?, 3, 'fourth')", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "insert into event_media (event_id, position, url) values (?, 6, 'https://img.example/7.png')", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update events set short_description = ? where id = ?", "x".repeat(161), id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingAnEventRemovesItsPreviewRows() throws Exception {
        long id = create(preview("Pitch", "[\"a\"]", "[\"b\"]", "[{\"url\":\"https://img.example/1.png\"}]"));
        assertThat(count("event_media", id)).isEqualTo(1);

        mockMvc.perform(delete("/api/events/" + id).with(organizer())).andExpect(status().isNoContent());

        assertThat(count("event_audience_fit", id)).isZero();
        assertThat(count("event_audience_not_fit", id)).isZero();
        assertThat(count("event_media", id)).isZero();
    }

    // ---------- helpers ----------

    private long create(String previewJson) throws Exception {
        String response = mockMvc.perform(post("/api/events").with(organizer())
                        .contentType(MediaType.APPLICATION_JSON).content(body("Preview event", previewJson)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private void update(long id, String previewJson) throws Exception {
        mockMvc.perform(put("/api/events/" + id).with(organizer())
                        .contentType(MediaType.APPLICATION_JSON).content(body("Preview event", previewJson)))
                .andExpect(status().isOk());
    }

    private static String body(String title, String previewJson) {
        return "{\"title\":\"" + title + "\",\"startsAt\":\"" + FUTURE + "\",\"capacity\":20"
                + (previewJson.isEmpty() ? "" : "," + previewJson) + "}";
    }

    private static String preview(String shortDescription, String fit, String notFit, String media) {
        return "\"shortDescription\":\"" + shortDescription + "\",\"audienceFit\":" + fit
                + ",\"audienceNotFit\":" + notFit + ",\"media\":" + media;
    }

    private long count(String table, long eventId) {
        return jdbc.queryForObject("select count(*) from " + table + " where event_id = ?", Long.class, eventId);
    }

    private static RequestPostProcessor organizer() {
        return user("1").roles("ORGANIZER");
    }
}
