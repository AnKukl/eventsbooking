package com.example.eventsbooking.user;

import com.example.eventsbooking.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeController.class)
@Import(SecurityConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MeControllerTest {

    private final MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AuthService service;

    MeControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    @WithMockUser(username = "7", roles = "ORGANIZER")
    void returnsTheAuthenticatedUserWithoutAnyPasswordData() throws Exception {
        User user = new User("org@example.com", "$2a$10$secret-hash", Role.ORGANIZER, Instant.parse("2030-01-01T10:00:00Z"));
        when(service.profile(7L)).thenReturn(user);

        mockMvc.perform(get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("org@example.com"))
                .andExpect(jsonPath("$.role").value("ORGANIZER"))
                .andExpect(content().string(not(containsString("hash"))))
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    @WithMockUser(username = "7", roles = "USER")
    void tokenOfADeletedUserReturns401() throws Exception {
        when(service.profile(7L)).thenThrow(new BadCredentialsException("Unknown user"));

        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "not-a-number", roles = "USER")
    void tokenWithANonNumericSubjectReturns401() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void anonymousGets401() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }
}
