package com.example.eventsbooking.user;

import com.example.eventsbooking.support.MutableClock;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Refresh tokens with rotation and reuse detection, against a real PostgreSQL and a clock the test controls. */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true",
        "app.security.jwt.ttl=PT15M",
        "app.security.refresh.ttl=P14D",
        "app.security.refresh.reuse-grace=PT10S"
})
@AutoConfigureMockMvc
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RefreshTokenIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    private static final Instant START = Instant.parse("2030-01-01T10:00:00Z");
    private static final String FRONTEND = "http://localhost:5173";
    private static final String PASSWORD = "correct-horse-battery-staple";
    private static final AtomicInteger USERS = new AtomicInteger();

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(START);
        }
    }

    /** What the browser holds after a login or a refresh. */
    record Session(String access, String refresh) {
    }

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final MutableClock clock;

    RefreshTokenIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc, MutableClock clock) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @BeforeEach
    void resetClock() {
        clock.set(START);
    }

    @Test
    void loginSetsAStrictRefreshCookieOnlyForTheAuthPaths() throws Exception {
        MockHttpServletResponse response = loginResponse(newUser());

        String refreshHeader = response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith("refresh_token=")).findFirst().orElseThrow();
        assertThat(refreshHeader).contains("HttpOnly", "SameSite=Strict", "Path=/api/auth", "Max-Age=1209600", "Secure");
    }

    @Test
    void onlyTheHashOfARefreshTokenIsStored() throws Exception {
        Session session = login(newUser());

        Map<String, Object> row = jdbc.queryForMap("select * from refresh_tokens order by id desc limit 1");
        assertThat(row.values()).noneMatch(value -> session.refresh().equals(String.valueOf(value)));
        assertThat((String) row.get("token_hash")).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void aRefreshRotatesBothCookiesAndTheNewAccessTokenWorks() throws Exception {
        Session first = login(newUser());
        clock.advance(Duration.ofMinutes(20));                    // the access token has expired
        mockMvc.perform(get("/api/me").cookie(new Cookie("access_token", first.access())))
                .andExpect(status().isUnauthorized());

        Session second = refresh(first.refresh());

        assertThat(second.refresh()).isNotEqualTo(first.refresh());
        assertThat(second.access()).isNotEqualTo(first.access());
        mockMvc.perform(get("/api/me").cookie(new Cookie("access_token", second.access())))
                .andExpect(status().isOk());
        Map<String, Object> old = jdbc.queryForMap(
                "select revoked_at, replaced_by from refresh_tokens where id = (select min(id) from refresh_tokens "
                        + "where family_id = (select family_id from refresh_tokens order by id desc limit 1))");
        assertThat(old.get("revoked_at")).isNotNull();
        assertThat(old.get("replaced_by")).isNotNull();
    }

    @Test
    void reusingARotatedTokenLaterRevokesTheWholeFamily() throws Exception {
        Session first = login(newUser());
        Session second = refresh(first.refresh());
        clock.advance(Duration.ofMinutes(5));                     // long after the rotation: not a race but a stolen copy

        refreshFails(first.refresh());

        refreshFails(second.refresh());                           // the legitimate holder is logged out as well
    }

    @Test
    void twoTabsRefreshingAtOnceGiveTheSecondA401ButKeepTheSession() throws Exception {
        Session first = login(newUser());
        Session winner = refresh(first.refresh());
        clock.advance(Duration.ofSeconds(3));                     // the other tab arrives with the same old token

        refreshFails(first.refresh());

        Session next = refresh(winner.refresh());                 // the family is still alive
        assertThat(next.refresh()).isNotEqualTo(winner.refresh());
    }

    @Test
    void anExpiredRefreshTokenGives401() throws Exception {
        Session first = login(newUser());
        clock.advance(Duration.ofDays(15));

        refreshFails(first.refresh());
    }

    @Test
    void aRefreshWithoutTheCookieGives401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").header(HttpHeaders.ORIGIN, FRONTEND))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheCurrentRefreshToken() throws Exception {
        Session session = login(newUser());

        mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.ORIGIN, FRONTEND)
                        .cookie(new Cookie("access_token", session.access()), new Cookie("refresh_token", session.refresh())))
                .andExpect(status().isNoContent());

        refreshFails(session.refresh());
    }

    @Test
    void aRejectedRefreshClearsTheCookies() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/auth/refresh").header(HttpHeaders.ORIGIN, FRONTEND)
                        .cookie(new Cookie("refresh_token", "not-a-real-token")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse();

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(header -> assertThat(header).startsWith("access_token=;").contains("Max-Age=0"))
                .anySatisfy(header -> assertThat(header).startsWith("refresh_token=;").contains("Max-Age=0"));
    }

    // ---------- helpers ----------

    private String newUser() throws Exception {
        String email = "refresh-" + USERS.incrementAndGet() + "@example.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
        return email;
    }

    private MockHttpServletResponse loginResponse(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse();
    }

    private Session login(String email) throws Exception {
        return session(loginResponse(email));
    }

    private Session refresh(String refreshToken) throws Exception {
        return session(mockMvc.perform(post("/api/auth/refresh").header(HttpHeaders.ORIGIN, FRONTEND)
                        .cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse());
    }

    private void refreshFails(String refreshToken) throws Exception {
        mockMvc.perform(post("/api/auth/refresh").header(HttpHeaders.ORIGIN, FRONTEND)
                        .cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    private static Session session(MockHttpServletResponse response) {
        Cookie access = response.getCookie("access_token");
        Cookie refresh = response.getCookie("refresh_token");
        assertThat(access).as("access_token cookie").isNotNull();
        assertThat(refresh).as("refresh_token cookie").isNotNull();
        return new Session(access.getValue(), refresh.getValue());
    }
}
