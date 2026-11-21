package com.example.eventsbooking.user;

import com.example.eventsbooking.common.ConflictException;
import com.example.eventsbooking.common.TooManyRequestsException;
import com.example.eventsbooking.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {MeController.class, EmailConfirmationController.class})
@Import(SecurityConfig.class)
@WithMockUser(username = "7")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ProfileControllerTest {

    private final MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private ProfileService service;

    ProfileControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    // ---------- PATCH /api/me ----------

    @Test
    void patchUpdatesTheProfileAndReturnsIt() throws Exception {
        User updated = user();
        updated.updateProfile("Anna", "+48123456789", "PL", "Warsaw");
        when(service.updateProfile(eq(7L), any(ProfileUpdateRequest.class))).thenReturn(updated);

        patch("{\"name\":\"Anna\",\"phone\":\"+48123456789\",\"language\":\"PL\",\"city\":\"Warsaw\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Anna"))
                .andExpect(jsonPath("$.phone").value("+48123456789"))
                .andExpect(jsonPath("$.language").value("PL"))
                .andExpect(jsonPath("$.city").value("Warsaw"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        var captor = org.mockito.ArgumentCaptor.forClass(ProfileUpdateRequest.class);
        verify(service).updateProfile(eq(7L), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new ProfileUpdateRequest("Anna", "+48123456789", "PL", "Warsaw"));
    }

    @Test
    void anUnsupportedLanguageIsRejectedWithTheFieldNamed() throws Exception {
        assertRejected(patch("{\"language\":\"DE\"}"), "language");
        assertRejected(patch("{\"language\":\"pl\"}"), "language");
        assertRejected(patch("{\"language\":\"\"}"), "language");
    }

    @Test
    void theSupportedLanguagesAreRUPLAndEN() throws Exception {
        when(service.updateProfile(eq(7L), any())).thenReturn(user());

        for (String language : new String[] {"RU", "PL", "EN"}) {
            patch("{\"language\":\"" + language + "\"}").andExpect(status().isOk());
        }
    }

    @Test
    void aPhoneMustBeInInternationalFormat() throws Exception {
        for (String phone : new String[] {"123456", "48123456789", "+48 123 456 789", "+0123456789", "+48123",
                "+481234567890123456", "abc", "+48-123-456-789"}) {
            assertRejected(patch("{\"phone\":\"" + phone + "\"}"), "phone");
        }
    }

    @Test
    void validPhonesAndAnEmptyPhoneAreAccepted() throws Exception {
        when(service.updateProfile(eq(7L), any())).thenReturn(user());

        for (String phone : new String[] {"+48123456789", "+79991234567", "+12025550123", ""}) {
            patch("{\"phone\":\"" + phone + "\"}").andExpect(status().isOk());
        }
    }

    @Test
    void tooLongNameAndCityAreRejected() throws Exception {
        assertRejected(patch("{\"name\":\"" + "n".repeat(101) + "\"}"), "name");
        assertRejected(patch("{\"city\":\"" + "c".repeat(101) + "\"}"), "city");
    }

    @Test
    void theEmailCannotBeChangedThroughPatch() throws Exception {
        when(service.updateProfile(eq(7L), any())).thenReturn(user());

        patch("{\"email\":\"hacker@example.com\",\"name\":\"A\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("old@example.com"));

        var captor = org.mockito.ArgumentCaptor.forClass(ProfileUpdateRequest.class);
        verify(service).updateProfile(eq(7L), captor.capture());
        assertThat(captor.getValue().toString()).doesNotContain("hacker");
    }

    @Test
    @WithAnonymousUser
    void guestsCannotPatch() throws Exception {
        patch("{\"name\":\"A\"}").andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    // ---------- POST /api/me/email-change ----------

    @Test
    void anEmailChangeRequestIsAcceptedAndNothingIsReturnedYet() throws Exception {
        emailChange("{\"newEmail\":\"new@example.com\"}").andExpect(status().isAccepted());

        verify(service).requestEmailChange(7L, "new@example.com");
    }

    @Test
    void anInvalidNewEmailIsRejected() throws Exception {
        assertRejected(emailChange("{\"newEmail\":\"not-an-email\"}"), "newEmail");
        assertRejected(emailChange("{}"), "newEmail");
    }

    @Test
    void tooFrequentRequestsGet429WithRetryAfterAsProblemDetail() throws Exception {
        org.mockito.Mockito.doThrow(new TooManyRequestsException(Duration.ofSeconds(42)))
                .when(service).requestEmailChange(7L, "new@example.com");

        emailChange("{\"newEmail\":\"new@example.com\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "42"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void aTakenEmailIs409() throws Exception {
        org.mockito.Mockito.doThrow(new ConflictException("Email already in use", "taken"))
                .when(service).requestEmailChange(7L, "taken@example.com");

        emailChange("{\"newEmail\":\"taken@example.com\"}").andExpect(status().isConflict());
    }

    // ---------- POST /api/auth/confirm-email (public: the token is the credential) ----------

    @Test
    @WithAnonymousUser
    void confirmingNeedsNoLogin() throws Exception {
        confirm("{\"token\":\"abc\"}").andExpect(status().isNoContent());

        verify(service).confirmEmail("abc");
    }

    @Test
    @WithAnonymousUser
    void aBadTokenIs400ProblemDetail() throws Exception {
        org.mockito.Mockito.doThrow(new InvalidVerificationTokenException()).when(service).confirmEmail("bad");

        confirm("{\"token\":\"bad\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @WithAnonymousUser
    void aBlankTokenIsRejected() throws Exception {
        assertRejected(confirm("{\"token\":\"  \"}"), "token");
        assertRejected(confirm("{}"), "token");
    }

    @Test
    void theTokenIsNeverPartOfTheRequestObjectsStringForm() {
        assertThat(new ConfirmEmailRequest("super-secret-token").toString()).doesNotContain("super-secret-token");
    }

    // ---------- helpers ----------

    private ResultActions patch(String body) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/me")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions emailChange(String body) throws Exception {
        return mockMvc.perform(post("/api/me/email-change").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions confirm(String body) throws Exception {
        return mockMvc.perform(post("/api/auth/confirm-email").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void assertRejected(ResultActions result, String field) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[?(@.field=='" + field + "')]").exists());
        verifyNoInteractions(service);
    }

    private static User user() {
        User user = new User("old@example.com", "$2a$10$secret-hash", Role.USER, Instant.parse("2030-01-01T10:00:00Z"));
        ReflectionTestUtils.setField(user, "id", 7L);
        return user;
    }
}
