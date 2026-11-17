package com.example.eventsbooking.event;





import com.example.eventsbooking.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Role rules of the filter chain: 401 without a valid token, 403 for USER, success for ORGANIZER. */
@WebMvcTest(EventController.class)
@Import(SecurityConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class EventSecurityTest {

    private static final String BODY = """
            {"title": "Jazz night", "description": "d", "startsAt": "2099-01-01T00:00:00Z",
             "capacity": 50, "organizerId": 1}
            """;

    private final MockMvc mockMvc;

    @MockitoBean
    private EventService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    EventSecurityTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    // ---------- 401: no or invalid credentials ----------

    @Test
    void createWithoutTokenReturns401ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));

        verifyNoInteractions(service);
    }

    @Test
    void readingWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/events/5")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/events")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenReturns401ProblemDetail() throws Exception {
        when(jwtDecoder.decode("garbage")).thenThrow(new BadJwtException("bad token"));

        mockMvc.perform(post("/api/events").header(HttpHeaders.AUTHORIZATION, "Bearer garbage")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(service);
    }

    // ---------- 403: authenticated but not an organizer ----------

    @Test
    void userCannotCreateEvents() throws Exception {
        mockMvc.perform(post("/api/events").with(user()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"));

        verifyNoInteractions(service);
    }

    @Test
    void userCannotUpdateEvents() throws Exception {
        mockMvc.perform(put("/api/events/5").with(user()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void userCannotDeleteEvents() throws Exception {
        mockMvc.perform(delete("/api/events/5").with(user())).andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    // ---------- success paths ----------

    @Test
    void organizerCanCreateEvents() throws Exception {
        when(service.create(any(EventRequest.class))).thenReturn(event(5L));

        mockMvc.perform(post("/api/events").with(organizer()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void organizerCanUpdateEvents() throws Exception {
        when(service.update(org.mockito.ArgumentMatchers.eq(5L), any(EventRequest.class))).thenReturn(event(5L));

        mockMvc.perform(put("/api/events/5").with(organizer()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void organizerCanDeleteEvents() throws Exception {
        mockMvc.perform(delete("/api/events/5").with(organizer())).andExpect(status().isNoContent());
    }

    @Test
    void userCanReadEvents() throws Exception {
        when(service.get(5L)).thenReturn(event(5L));
        when(service.list(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(event(5L))));

        mockMvc.perform(get("/api/events/5").with(user())).andExpect(status().isOk());
        mockMvc.perform(get("/api/events").with(user())).andExpect(status().isOk());
    }

    // ---------- helpers ----------

    private static RequestPostProcessor user() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static RequestPostProcessor organizer() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"));
    }

    private static Event event(long id) {
        Event event = new Event("Jazz night", "d", Instant.parse("2099-01-01T00:00:00Z"), 50, 1L);
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }
}
