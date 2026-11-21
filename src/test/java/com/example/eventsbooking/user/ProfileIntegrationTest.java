package com.example.eventsbooking.user;

import com.example.eventsbooking.support.MutableClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Profile and the email confirmation flow against a real PostgreSQL, a controllable clock and a recording mailer. */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true"
})
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ProfileIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    private static final String PASSWORD = "S3cret-Pass-123";

    record SentMail(long userId, String email, String token) {
    }

    static class RecordingEmailSender implements EmailSender {
        final List<SentMail> sent = new CopyOnWriteArrayList<>();

        @Override
        public void sendVerification(long userId, String email, String token) {
            sent.add(new SentMail(userId, email, token));
        }

        SentMail last() {
            return sent.get(sent.size() - 1);
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2030-01-01T10:00:00Z"));
        }

        @Bean
        @Primary
        RecordingEmailSender recordingEmailSender() {
            return new RecordingEmailSender();
        }
    }

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final MutableClock clock;
    private final RecordingEmailSender mailer;
    private final ObjectMapper json = new ObjectMapper();

    ProfileIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbc, MutableClock clock, RecordingEmailSender mailer) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.clock = clock;
        this.mailer = mailer;
    }

    @Test
    void theProfileCanBePatchedAndReadBackAndInvalidValuesChangeNothing() throws Exception {
        long id = register("profile@example.com");

        patchMe(id, "{\"name\":\"Anna\",\"phone\":\"+48123456789\",\"language\":\"PL\",\"city\":\"Warsaw\"}")
                .andExpect(status().isOk());
        patchMe(id, "{\"language\":\"DE\",\"name\":\"Hacked\"}").andExpect(status().isBadRequest());
        patchMe(id, "{\"phone\":\"12345\"}").andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/me").with(asUser(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Anna"))
                .andExpect(jsonPath("$.phone").value("+48123456789"))
                .andExpect(jsonPath("$.language").value("PL"))
                .andExpect(jsonPath("$.city").value("Warsaw"))
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    void patchCannotChangeTheEmail() throws Exception {
        long id = register("sticky@example.com");

        patchMe(id, "{\"email\":\"hacker@example.com\"}").andExpect(status().isOk());

        mockMvc.perform(get("/api/me").with(asUser(id))).andExpect(jsonPath("$.email").value("sticky@example.com"));
    }

    @Test
    void anEmailChangeTakesEffectOnlyAfterTheConfirmationAndTheTokenWorksOnce() throws Exception {
        long id = register("before@example.com");

        requestChange(id, "after@example.com").andExpect(status().isAccepted());
        String token = mailer.last().token();

        mockMvc.perform(get("/api/me").with(asUser(id)))
                .andExpect(jsonPath("$.email").value("before@example.com"))
                .andExpect(jsonPath("$.emailVerified").value(false));
        assertThat(jdbc.queryForObject("select token_hash from email_verifications where user_id = ?",
                String.class, id)).hasSize(64).isNotEqualTo(token);

        confirm(token).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me").with(asUser(id)))
                .andExpect(jsonPath("$.email").value("after@example.com"))
                .andExpect(jsonPath("$.emailVerified").value(true));
        confirm(token).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"after@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void theTokenExpiresAfter24Hours() throws Exception {
        long id = register("slow@example.com");
        requestChange(id, "slow-new@example.com").andExpect(status().isAccepted());
        String token = mailer.last().token();

        clock.advance(Duration.ofHours(24));

        confirm(token).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/me").with(asUser(id))).andExpect(jsonPath("$.email").value("slow@example.com"));
    }

    @Test
    void aTokenIsStillValidJustBeforeItExpires() throws Exception {
        long id = register("quick@example.com");
        requestChange(id, "quick-new@example.com").andExpect(status().isAccepted());
        String token = mailer.last().token();

        clock.advance(Duration.ofHours(24).minusSeconds(1));

        confirm(token).andExpect(status().isNoContent());
    }

    @Test
    void resendingIsLimitedToOncePerMinuteAndTheOldTokenStopsWorking() throws Exception {
        long id = register("resend@example.com");
        requestChange(id, "resend-new@example.com").andExpect(status().isAccepted());
        String first = mailer.last().token();

        requestChange(id, "resend-new@example.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.status").value(429));
        assertThat(mailer.sent.stream().filter(mail -> mail.userId() == id)).hasSize(1);

        clock.advance(Duration.ofSeconds(60));
        requestChange(id, "resend-new@example.com").andExpect(status().isAccepted());
        String second = mailer.last().token();

        assertThat(second).isNotEqualTo(first);
        confirm(first).andExpect(status().isBadRequest());
        confirm(second).andExpect(status().isNoContent());
    }

    @Test
    void anEmailOfAnotherAccountIsAConflictAndNothingIsSent() throws Exception {
        long id = register("one@example.com");
        register("two@example.com");
        int before = mailer.sent.size();

        requestChange(id, "TWO@example.com").andExpect(status().isConflict());

        assertThat(mailer.sent).hasSize(before);
    }

    @Test
    void tokensAndPasswordsNeverReachTheLogs(CapturedOutput output) throws Exception {
        long id = register("quiet@example.com");
        requestChange(id, "quiet-new@example.com").andExpect(status().isAccepted());
        String token = mailer.last().token();
        requestChange(id, "quiet-new@example.com").andExpect(status().isTooManyRequests());
        confirm("wrong-" + token).andExpect(status().isBadRequest());
        confirm(token).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"quiet-new@example.com\",\"password\":\"" + PASSWORD + "\"}"));

        assertThat(output.getAll()).doesNotContain(token).doesNotContain(PASSWORD).doesNotContain("eyJ");
    }

    @Test
    void guestsCannotPatchOrRequestAnEmailChange() throws Exception {
        mockMvc.perform(patch("/api/me").with(anonymous()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/me/email-change").with(anonymous()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newEmail\":\"a@example.com\"}")).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private long register(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private ResultActions patchMe(long id, String body) throws Exception {
        return mockMvc.perform(patch("/api/me").with(asUser(id)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions requestChange(long id, String newEmail) throws Exception {
        return mockMvc.perform(post("/api/me/email-change").with(asUser(id)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newEmail\":\"" + newEmail + "\"}"));
    }

    private ResultActions confirm(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/confirm-email").with(anonymous()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }

    private static RequestPostProcessor asUser(long id) {
        return user(Long.toString(id)).roles("USER");
    }
}
