package com.example.eventsbooking.organizer;

import com.example.eventsbooking.support.TestImages;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The organizer cabinet against a real PostgreSQL and a real upload directory. */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true",
        "app.storage.local-dir=target/test-uploads/organizer-it"
})
@AutoConfigureMockMvc
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OrganizerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    private static final Path UPLOADS = Path.of("target/test-uploads/organizer-it");
    private static final String FUTURE = Instant.now().plus(30, ChronoUnit.DAYS).toString();

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    OrganizerIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    // ---------- drafts are private ----------

    @Test
    void aDraftIsInvisibleAndUnusableForThePublic() throws Exception {
        long draft = draft(organizer(1));

        mockMvc.perform(get("/api/events/" + draft).with(anonymous())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/events/" + draft + "/preview").with(anonymous())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/events").param("size", "100").with(anonymous()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.id==" + draft + ")]").isEmpty());
        mockMvc.perform(post("/api/bookings").with(asUser(5001)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":" + draft + "}")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/favorites/" + draft).with(asUser(5001))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/events/" + draft + "/waitlist").with(asUser(5001))
                .contentType(MediaType.APPLICATION_JSON).content("{\"channel\":\"EMAIL\"}")).andExpect(status().isNotFound());
        // ... and not even the owner sees it through the public endpoint: the cabinet is the place for drafts
        mockMvc.perform(get("/api/organizer/events/" + draft).with(organizer(1))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    // ---------- publishing ----------

    @Test
    void publishingListsWhatIsMissingUntilTheEventIsComplete() throws Exception {
        long id = draft(organizer(1));

        mockMvc.perform(put("/api/organizer/events/" + id + "/publish").with(organizer(1)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[0].field").value("venue"))
                .andExpect(jsonPath("$.errors[1].field").value("price"))
                .andExpect(jsonPath("$.errors[2].field").value("media"));

        patch(id, organizer(1), "{\"version\":0,\"venue\":\"Main hall\",\"price\":15.50}").andExpect(status().isOk());
        mockMvc.perform(put("/api/organizer/events/" + id + "/publish").with(organizer(1)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("media"));

        upload(id, organizer(1), new MockMultipartFile("files", "cover.png", "image/png", TestImages.png()))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/organizer/events/" + id + "/publish").with(organizer(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));

        mockMvc.perform(get("/api/events/" + id).with(anonymous())).andExpect(status().isOk());
        mockMvc.perform(get("/api/events/" + id + "/preview").with(anonymous()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.media[0].url").value(org.hamcrest.Matchers.startsWith("/api/media/")));
    }

    // ---------- photos ----------

    @Test
    void aFileWithASwappedExtensionIsRejectedAndNothingIsKept() throws Exception {
        long id = draft(organizer(1));
        long filesBefore = filesInUploads();

        upload(id, organizer(1), new MockMultipartFile("files", "photo.jpg", "image/jpeg", TestImages.text()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        upload(id, organizer(1), new MockMultipartFile("files", "photo.png", "image/png", TestImages.exe()))
                .andExpect(status().isUnsupportedMediaType());
        upload(id, organizer(1), new MockMultipartFile("files", "photo.jpg", "image/jpeg", TestImages.png()))
                .andExpect(status().isUnsupportedMediaType());   // a real PNG claiming to be a JPEG
        upload(id, organizer(1), new MockMultipartFile("files", "anim.gif", "image/gif", TestImages.gif()))
                .andExpect(status().isUnsupportedMediaType());

        assertThat(jdbc.queryForObject("select count(*) from event_media where event_id = ?", Long.class, id)).isZero();
        assertThat(filesInUploads()).isEqualTo(filesBefore);
    }

    @Test
    void anOversizedFileIsRejectedWith413() throws Exception {
        long id = draft(organizer(1));

        upload(id, organizer(1), new MockMultipartFile("files", "huge.png", "image/png",
                TestImages.pngOfSize(8 * 1024 * 1024 + 1))).andExpect(status().isContentTooLarge());
    }

    @Test
    void anEventHoldsSixPhotosAtMostAndUploadedPhotosAreServedToTheWorld() throws Exception {
        long id = draft(organizer(1));
        String url = null;
        for (int i = 0; i < 6; i++) {
            String body = upload(id, organizer(1), new MockMultipartFile("files", "p" + i + ".png", "image/png", TestImages.png()))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            url = json.readTree(body).get(0).get("url").asText();
        }

        upload(id, organizer(1), new MockMultipartFile("files", "seventh.png", "image/png", TestImages.png()))
                .andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("select count(*) from event_media where event_id = ?", Long.class, id)).isEqualTo(6);
        mockMvc.perform(get(url).with(anonymous())).andExpect(status().isOk())
                .andExpect(content().contentType("image/png")).andExpect(content().bytes(TestImages.png()));
    }

    @Test
    void severalFilesInOneRequestAreStoredInOrder() throws Exception {
        long id = draft(organizer(1));

        String body = mockMvc.perform(multipart("/api/organizer/events/" + id + "/media")
                        .file(new MockMultipartFile("files", "a.png", "image/png", TestImages.png()))
                        .file(new MockMultipartFile("files", "b.webp", "image/webp", TestImages.webp()))
                        .with(organizer(1)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(body)).hasSize(2);
        assertThat(json.readTree(body).get(0).get("url").asText()).endsWith(".png");
        assertThat(json.readTree(body).get(1).get("url").asText()).endsWith(".webp");
        assertThat(jdbc.queryForList("select url from event_media where event_id = ? order by position", String.class, id))
                .hasSize(2);
    }

    // ---------- ownership ----------

    @Test
    void aForeignEventIsForbiddenEverywhere() throws Exception {
        long id = draft(organizer(1));
        RequestPostProcessor rival = organizer(2);

        mockMvc.perform(get("/api/organizer/events/" + id).with(rival)).andExpect(status().isForbidden());
        patch(id, rival, "{\"version\":0,\"title\":\"Hijacked\"}").andExpect(status().isForbidden());
        mockMvc.perform(put("/api/organizer/events/" + id + "/publish").with(rival)).andExpect(status().isForbidden());
        upload(id, rival, new MockMultipartFile("files", "a.png", "image/png", TestImages.png())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/organizer/events/" + id + "/bookings").with(rival)).andExpect(status().isForbidden());

        mockMvc.perform(get("/api/organizer/events/" + id).with(organizer(1)))
                .andExpect(jsonPath("$.title").value("Cabinet event"))
                .andExpect(jsonPath("$.version").value(0));
        assertThat(jdbc.queryForObject("select count(*) from event_media where event_id = ?", Long.class, id)).isZero();
    }

    @Test
    void anOrdinaryUserAndAGuestAreRefusedBeforeAnythingElse() throws Exception {
        mockMvc.perform(get("/api/organizer/events").with(asUser(5002))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/organizer/events").with(anonymous())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/organizer/analytics").with(asUser(5002))).andExpect(status().isForbidden());
    }

    // ---------- the version ----------

    @Test
    void aStaleVersionIsRejectedAndBookingsDoNotMakeAFormStale() throws Exception {
        long id = publishedEvent(organizer(1), 10);

        patch(id, organizer(1), "{\"version\":3,\"title\":\"First\"}").andExpect(status().isOk());
        patch(id, organizer(1), "{\"version\":3,\"title\":\"Late\"}").andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        // a booking changes the seat counter, but not the version an organizer edits against
        mockMvc.perform(post("/api/bookings").with(asUser(5003)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":" + id + "}")).andExpect(status().isCreated());
        mockMvc.perform(get("/api/organizer/events/" + id).with(organizer(1)))
                .andExpect(jsonPath("$.title").value("First")).andExpect(jsonPath("$.version").value(4))
                .andExpect(jsonPath("$.bookedCount").value(1));
        patch(id, organizer(1), "{\"version\":4,\"title\":\"Second\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(5));
    }

    @Test
    void theWaitlistCanBeSwitchedOffPerEvent() throws Exception {
        long id = publishedEvent(organizer(1), 1);
        mockMvc.perform(post("/api/bookings").with(asUser(5004)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":" + id + "}")).andExpect(status().isCreated());
        long version = json.readTree(mockMvc.perform(get("/api/organizer/events/" + id).with(organizer(1)))
                .andReturn().getResponse().getContentAsString()).get("version").asLong();

        mockMvc.perform(post("/api/events/" + id + "/waitlist").with(asUser(5005)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"EMAIL\"}")).andExpect(status().isCreated());
        patch(id, organizer(1), "{\"version\":" + version + ",\"waitlistEnabled\":false}").andExpect(status().isOk());

        mockMvc.perform(post("/api/events/" + id + "/waitlist").with(asUser(5006)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"EMAIL\"}")).andExpect(status().isConflict());
    }

    // ---------- the list ----------

    @Test
    void theListShowsOnlyMyEventsFilteredByStatus() throws Exception {
        RequestPostProcessor me = organizer(31);
        long draft = draft(me);
        long open = publishedEvent(me, 5);
        long soldOut = publishedEvent(me, 1);
        mockMvc.perform(post("/api/bookings").with(asUser(5007)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":" + soldOut + "}")).andExpect(status().isCreated());
        draft(organizer(32));   // someone else's

        assertThat(ids(me, null)).containsExactlyInAnyOrder(draft, open, soldOut);
        assertThat(ids(me, "DRAFT")).containsExactly(draft);
        assertThat(ids(me, "PUBLISHED")).containsExactly(open);
        assertThat(ids(me, "SOLD_OUT")).containsExactly(soldOut);
        assertThat(ids(me, "CANCELLED")).isEmpty();
        mockMvc.perform(get("/api/organizer/events").param("size", "2").param("page", "1").with(me))
                .andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.page.totalElements").value(3));
    }

    // ---------- bookings of an event ----------

    @Test
    void theBookingsOfAnEventShowNoContactDetails() throws Exception {
        long id = publishedEvent(organizer(1), 10);
        jdbc.update("insert into users (email, password_hash, role, name, phone) values "
                + "('secret.attendee@example.com', 'hash', 'USER', 'Anna', '+48123456789')");
        long attendee = jdbc.queryForObject("select id from users where email = 'secret.attendee@example.com'", Long.class);
        mockMvc.perform(post("/api/bookings").with(asUser(attendee)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":" + id + ",\"seats\":2}")).andExpect(status().isCreated());

        String body = mockMvc.perform(get("/api/organizer/events/" + id + "/bookings").with(organizer(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("HOLD"))
                .andExpect(jsonPath("$.content[0].seats").value(2))
                .andExpect(jsonPath("$.content[0].attendee.name").value("Anna"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("secret.attendee").doesNotContain("48123456789").doesNotContain("hash")
                .doesNotContain("email").doesNotContain("phone");
    }

    // ---------- analytics ----------

    @Test
    void analyticsAreComputedByTheDatabaseOverTheChosenRange() throws Exception {
        long mine = jdbc.queryForObject("insert into events (title, starts_at, capacity, organizer_id, price, status) values "
                + "('Mine', ?, 100, 61, 10.00, 'PUBLISHED') returning id", Long.class,
                Timestamp.from(Instant.now().plus(Duration.ofDays(20))));
        long free = jdbc.queryForObject("insert into events (title, starts_at, capacity, organizer_id, status) values "
                + "('Free', ?, 50, 61, 'PUBLISHED') returning id", Long.class,
                Timestamp.from(Instant.now().plus(Duration.ofDays(20))));
        long others = jdbc.queryForObject("insert into events (title, starts_at, capacity, organizer_id, price, status) values "
                + "('Theirs', ?, 100, 62, 99.00, 'PUBLISHED') returning id", Long.class,
                Timestamp.from(Instant.now().plus(Duration.ofDays(20))));
        booking(mine, 6001, 3, "CONFIRMED", Duration.ofDays(1));
        booking(mine, 6002, 2, "CONFIRMED", Duration.ofDays(10));        // older than a week
        booking(mine, 6003, 4, "HOLD", Duration.ofHours(1));             // not paid for
        booking(mine, 6004, 5, "CANCELLED", Duration.ofDays(1));
        booking(mine, 6005, 1, "REFUND_PENDING", Duration.ofDays(1));
        booking(free, 6006, 5, "CONFIRMED", Duration.ofDays(2));         // no price: sold, but no revenue
        booking(others, 6007, 50, "CONFIRMED", Duration.ofDays(1));      // another organizer's

        mockMvc.perform(get("/api/organizer/analytics").param("range", "7d").with(organizer(61)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("7d"))
                .andExpect(jsonPath("$.sold").value(8))                  // 3 + 5
                .andExpect(jsonPath("$.revenue").value(30.00))           // 3 * 10.00
                .andExpect(jsonPath("$.occupancy").value(0.0667));       // (3 + 2 + 5) / (100 + 50)
        mockMvc.perform(get("/api/organizer/analytics").param("range", "30d").with(organizer(61)))
                .andExpect(jsonPath("$.sold").value(10))
                .andExpect(jsonPath("$.revenue").value(50.00));
        mockMvc.perform(get("/api/organizer/analytics").with(organizer(63)))   // an organizer with nothing
                .andExpect(jsonPath("$.sold").value(0)).andExpect(jsonPath("$.revenue").value(0.0))
                .andExpect(jsonPath("$.occupancy").value(0.0));
        mockMvc.perform(get("/api/organizer/analytics").param("range", "366d").with(organizer(61)))
                .andExpect(status().isBadRequest());
    }

    // ---------- helpers ----------

    private long draft(RequestPostProcessor as) throws Exception {
        String body = mockMvc.perform(post("/api/organizer/events").with(as).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Cabinet event\",\"startsAt\":\"" + FUTURE + "\",\"capacity\":10}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private long publishedEvent(RequestPostProcessor as, int capacity) throws Exception {
        long id = draft(as);
        patch(id, as, "{\"version\":0,\"capacity\":" + capacity + ",\"venue\":\"Hall\",\"price\":12.00}").andExpect(status().isOk());
        upload(id, as, new MockMultipartFile("files", "cover.png", "image/png", TestImages.png())).andExpect(status().isCreated());
        mockMvc.perform(put("/api/organizer/events/" + id + "/publish").with(as)).andExpect(status().isOk());
        return id;
    }

    private ResultActions patch(long id, RequestPostProcessor as, String body) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/organizer/events/" + id).with(as).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions upload(long id, RequestPostProcessor as, MockMultipartFile file) throws Exception {
        return mockMvc.perform(multipart("/api/organizer/events/" + id + "/media").file(file).with(as));
    }

    private java.util.List<Long> ids(RequestPostProcessor as, String status) throws Exception {
        var request = get("/api/organizer/events").param("size", "100").with(as);
        if (status != null) {
            request.param("status", status);
        }
        JsonNode page = json.readTree(mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        java.util.List<Long> ids = new java.util.ArrayList<>();
        page.get("content").forEach(item -> ids.add(item.get("id").asLong()));
        return ids;
    }

    private void booking(long event, long user, int seats, String status, Duration age) {
        Timestamp created = Timestamp.from(Instant.now().minus(age));
        jdbc.update("insert into bookings (event_id, user_id, seats, status, created_at, hold_expires_at) "
                + "values (?, ?, ?, ?, ?, ?)", event, user, seats, status, created,
                "HOLD".equals(status) ? Timestamp.from(Instant.now().plus(Duration.ofMinutes(5))) : null);
    }

    private long filesInUploads() throws IOException {
        if (!Files.exists(UPLOADS)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(UPLOADS)) {
            return files.count();
        }
    }

    private static RequestPostProcessor organizer(long id) {
        return user(Long.toString(id)).roles("ORGANIZER");
    }

    private static RequestPostProcessor asUser(long id) {
        return user(Long.toString(id)).roles("USER");
    }
}
