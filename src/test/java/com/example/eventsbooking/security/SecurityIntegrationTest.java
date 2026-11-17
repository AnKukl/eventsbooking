package com.example.eventsbooking.security;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real registration, real BCrypt, real signed tokens, real PostgreSQL. */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true"
})
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SecurityIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery-staple";
    private static final String EVENT = """
            {"title": "Secured event", "description": "d", "startsAt": "2099-01-01T00:00:00Z",
             "capacity": 10, "organizerId": 1}
            """;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;

    SecurityIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    @Test
    void anonymousRequestsToProtectedEndpointsGet401() throws Exception {
        mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(EVENT))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/events")).andExpect(status().isUnauthorized());
    }

    @Test
    void userGets403AndOrganizerGets201WhenCreatingAnEvent() throws Exception {
        String userToken = registerAndLogin("user-create@example.com", "USER");
        String organizerToken = registerAndLogin("org-create@example.com", "ORGANIZER");

        mockMvc.perform(post("/api/events").header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(EVENT))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(post("/api/events").header(HttpHeaders.AUTHORIZATION, "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(EVENT))
                .andExpect(status().isCreated());
    }

    @Test
    void userCanReadEventsWithAValidToken() throws Exception {
        String userToken = registerAndLogin("user-read@example.com", "USER");

        mockMvc.perform(get("/api/events").header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk());
    }

    @Test
    void tokenWithEscalatedRoleIsRejectedBecauseTheSignatureNoLongerMatches() throws Exception {
        String userToken = registerAndLogin("user-forge@example.com", "USER");
        String[] parts = userToken.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(
                payload.replace("\"USER\"", "\"ORGANIZER\"").getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        mockMvc.perform(post("/api/events").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged)
                        .contentType(MediaType.APPLICATION_JSON).content(EVENT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordIsStoredAsBCryptHash() throws Exception {
        register("hash-check@example.com", "USER");

        String stored = jdbc.queryForObject(
                "select password_hash from users where email = ?", String.class, "hash-check@example.com");

        assertThat(stored).matches("^\\$2[aby]\\$\\d{2}\\$.{53}$").doesNotContain(PASSWORD);
    }

    @Test
    void registeringTheSameEmailTwiceReturns409() throws Exception {
        register("dup@example.com", "USER");

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration("DUP@example.com", "USER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        register("wrong-pass@example.com", "USER");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("wrong-pass@example.com", "not-the-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void tokensAndPasswordsAreNeverWrittenToTheLog(CapturedOutput output) throws Exception {
        String token = registerAndLogin("log-check@example.com", "USER");
        mockMvc.perform(get("/api/events").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/events").header(HttpHeaders.AUTHORIZATION, "Bearer " + token + "x"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(credentials("log-check@example.com", "bad-password-123")));

        assertThat(output.getAll())
                .doesNotContain(token)
                .doesNotContain(PASSWORD)
                .doesNotContain("bad-password-123");
    }

    // ---------- helpers ----------

    private String registerAndLogin(String email, String role) throws Exception {
        register(email, role);
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private void register(String email, String role) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration(email, role)))
                .andExpect(status().isCreated());
    }

    private static String registration(String email, String role) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"role\":\"" + role + "\"}";
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }
}
