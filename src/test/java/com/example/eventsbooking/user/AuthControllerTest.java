package com.example.eventsbooking.user;

import com.example.eventsbooking.security.IssuedToken;
import com.example.eventsbooking.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuthControllerTest {

    private final MockMvc mockMvc;

    @MockitoBean
    private AuthService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    AuthControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    // ---------- registration ----------

    @Test
    void registerReturns201AndNeverExposesPasswordOrHash() throws Exception {
        User user = new User("alice@example.com", "$2a$10$hash", Role.USER, Instant.parse("2030-01-01T10:00:00Z"));
        ReflectionTestUtils.setField(user, "id", 7L);
        when(service.register(any(RegisterRequest.class))).thenReturn(user);

        register("{\"email\":\"alice@example.com\",\"password\":\"s3cret-password\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("$2a$"))));
    }

    @Test
    void registerRejectsInvalidEmail() throws Exception {
        assertInvalid(register("{\"email\":\"not-an-email\",\"password\":\"s3cret-password\"}"), "email");
    }

    @Test
    void registerRejectsMissingEmail() throws Exception {
        assertInvalid(register("{\"password\":\"s3cret-password\"}"), "email");
    }

    @Test
    void registerRejectsPasswordShorterThan8Characters() throws Exception {
        assertInvalid(register("{\"email\":\"alice@example.com\",\"password\":\"short\"}"), "password");
    }

    @Test
    void registerRejectsMissingPassword() throws Exception {
        assertInvalid(register("{\"email\":\"alice@example.com\"}"), "password");
    }

    @Test
    void registerRejectsPasswordLongerThan72Bytes() throws Exception {
        // 40 two-byte characters = 80 bytes: BCrypt would reject it
        assertInvalid(register("{\"email\":\"alice@example.com\",\"password\":\"" + "ж".repeat(40) + "\"}"),
                "passwordWithin72Bytes");
    }

    @Test
    void registerRejectsUnknownRole() throws Exception {
        register("{\"email\":\"alice@example.com\",\"password\":\"s3cret-password\",\"role\":\"ADMIN\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    void registerWithTakenEmailReturns409ProblemDetail() throws Exception {
        when(service.register(any(RegisterRequest.class))).thenThrow(new EmailAlreadyRegisteredException());

        register("{\"email\":\"alice@example.com\",\"password\":\"s3cret-password\"}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Email already registered"));
    }

    // ---------- login ----------

    @Test
    void loginReturnsBearerToken() throws Exception {
        when(service.login(any(LoginRequest.class))).thenReturn(new IssuedToken("jwt-value", 3600));

        login("{\"email\":\"alice@example.com\",\"password\":\"s3cret-password\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-value"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void loginWithWrongCredentialsReturns401ProblemDetail() throws Exception {
        when(service.login(any(LoginRequest.class))).thenThrow(new BadCredentialsException("x"));

        login("{\"email\":\"alice@example.com\",\"password\":\"wrong-password\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void loginRejectsBlankFields() throws Exception {
        login("{\"email\":\"\",\"password\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='password')]").exists());

        verifyNoInteractions(service);
    }

    // ---------- helpers ----------

    private ResultActions register(String body) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions login(String body) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void assertInvalid(ResultActions result, String field) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[?(@.field=='" + field + "')]").exists());

        verifyNoInteractions(service);
    }
}
