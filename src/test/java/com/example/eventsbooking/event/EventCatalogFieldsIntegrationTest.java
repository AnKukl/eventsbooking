package com.example.eventsbooking.event;

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

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The public catalogue shows a tile without extra requests: venue, price and the cover picture come with the event. */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true"
})
@AutoConfigureMockMvc
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class EventCatalogFieldsIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;

    EventCatalogFieldsIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    @Test
    void listAndSingleEventCarryVenuePriceAndTheFirstPictureAsCover() throws Exception {
        long id = event("Свет на мраморе", "Национальный музей, Гданьск", "45.00");
        media(id, 0, "/api/media/cover.png");
        media(id, 1, "/api/media/second.png");

        mockMvc.perform(get("/api/events").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + id + ")].venue").value(hasItem("Национальный музей, Гданьск")))
                .andExpect(jsonPath("$.content[?(@.id == " + id + ")].price").value(hasItem(45.0)))
                .andExpect(jsonPath("$.content[?(@.id == " + id + ")].coverUrl").value(hasItem("/api/media/cover.png")));
        mockMvc.perform(get("/api/events/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venue").value("Национальный музей, Гданьск"))
                .andExpect(jsonPath("$.price").value(45.0))
                .andExpect(jsonPath("$.coverUrl").value("/api/media/cover.png"));
    }

    @Test
    void anEventWithoutPicturesVenueOrPriceHasNulls() throws Exception {
        long id = event("Без деталей", null, null);

        mockMvc.perform(get("/api/events/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venue").doesNotExist())
                .andExpect(jsonPath("$.price").doesNotExist())
                .andExpect(jsonPath("$.coverUrl").doesNotExist());
    }

    @Test
    void favoritesCarryTheSameFields() throws Exception {
        long id = event("Маяки Балтики", "Gdańsk", "0.00");
        media(id, 0, "/api/media/lighthouse.png");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"catalog@example.com\",\"password\":\"correct-horse-battery\"}"))
                .andExpect(status().isCreated());
        String token = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"catalog@example.com\",\"password\":\"correct-horse-battery\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("access_token").getValue();
        Cookie session = new Cookie("access_token", token);
        mockMvc.perform(post("/api/favorites/" + id).cookie(session).header("Origin", "http://localhost"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/favorites").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].venue").value("Gdańsk"))
                .andExpect(jsonPath("$.content[0].price").value(0.0))
                .andExpect(jsonPath("$.content[0].coverUrl").value("/api/media/lighthouse.png"));
    }

    private long event(String title, String venue, String price) {
        jdbc.update("insert into events (title, starts_at, capacity, organizer_id, venue, price) "
                + "values (?, now() + interval '10 days', 20, 1, ?, ?::numeric)", title, venue, price);
        return jdbc.queryForObject("select max(id) from events", Long.class);
    }

    private void media(long eventId, int position, String url) {
        jdbc.update("insert into event_media (event_id, position, url) values (?, ?, ?)", eventId, position, url);
    }
}
