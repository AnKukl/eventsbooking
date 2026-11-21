package com.example.eventsbooking.waitlist;

import com.example.eventsbooking.common.ConflictException;
import com.example.eventsbooking.event.EventNotFoundException;
import com.example.eventsbooking.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WaitlistController.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "7")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class WaitlistControllerTest {

    private final MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private WaitlistService service;

    WaitlistControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void firstJoinReturns201AndUsesTheUserFromTheToken() throws Exception {
        when(service.join(7L, 5L, WaitlistChannel.PUSH)).thenReturn(true);

        mockMvc.perform(post("/api/events/5/waitlist").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"PUSH\"}"))
                .andExpect(status().isCreated());

        verify(service).join(7L, 5L, WaitlistChannel.PUSH);
    }

    @Test
    void repeatedJoinReturns200() throws Exception {
        when(service.join(7L, 5L, WaitlistChannel.EMAIL)).thenReturn(false);

        mockMvc.perform(post("/api/events/5/waitlist").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"EMAIL\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void aMissingChannelIsRejectedWithValidationProblemDetail() throws Exception {
        mockMvc.perform(post("/api/events/5/waitlist").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[?(@.field=='channel')]").exists());

        verifyNoInteractions(service);
    }

    @Test
    void anUnknownChannelIsRejectedWith400ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/events/5/waitlist").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"SMS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    void aMissingBodyIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/api/events/5/waitlist")).andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void unknownEventIs404ProblemDetail() throws Exception {
        when(service.join(7L, 99L, WaitlistChannel.EMAIL)).thenThrow(new EventNotFoundException(99L));

        mockMvc.perform(post("/api/events/99/waitlist").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"EMAIL\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void aBusinessConflictIs409ProblemDetail() throws Exception {
        when(service.join(7L, 5L, WaitlistChannel.EMAIL))
                .thenThrow(new ConflictException("Event has free seats", "Book the event instead"));

        mockMvc.perform(post("/api/events/5/waitlist").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"EMAIL\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Event has free seats"));
    }

    @Test
    @WithAnonymousUser
    void guestsGet401ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/events/5/waitlist").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"EMAIL\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(service);
    }
}
