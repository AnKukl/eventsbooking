package com.example.eventsbooking.notification;

import com.example.eventsbooking.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationPreferencesController.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "7")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class NotificationPreferencesControllerTest {

    private final MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private NotificationPreferenceService service;

    NotificationPreferencesControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void getReturnsThePreferencesOfTheCaller() throws Exception {
        when(service.get(7L)).thenReturn(List.of(
                new NotificationPreference(NotificationType.BOOKING_CONFIRMED, true, false),
                new NotificationPreference(NotificationType.SEAT_AVAILABLE, false, true)));

        mockMvc.perform(get("/api/me/notifications/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences.length()").value(2))
                .andExpect(jsonPath("$.preferences[0].type").value("BOOKING_CONFIRMED"))
                .andExpect(jsonPath("$.preferences[0].email").value(true))
                .andExpect(jsonPath("$.preferences[0].push").value(false))
                .andExpect(jsonPath("$.preferences[1].type").value("SEAT_AVAILABLE"));
    }

    @Test
    void putStoresTheGivenPreferencesForTheUserFromTheToken() throws Exception {
        when(service.update(eq(7L), any())).thenReturn(List.of(
                new NotificationPreference(NotificationType.REMINDER_24H, false, true)));

        put("{\"preferences\":[{\"type\":\"REMINDER_24H\",\"email\":false,\"push\":true}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences[0].type").value("REMINDER_24H"))
                .andExpect(jsonPath("$.preferences[0].email").value(false));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationPreference>> captor = ArgumentCaptor.forClass(List.class);
        verify(service).update(eq(7L), captor.capture());
        assertThat(captor.getValue())
                .containsExactly(new NotificationPreference(NotificationType.REMINDER_24H, false, true));
    }

    @Test
    void anUnknownTypeIsRejected() throws Exception {
        put("{\"preferences\":[{\"type\":\"SPAM\",\"email\":true,\"push\":true}]}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    void aMissingChannelFlagIsRejectedWithItsPath() throws Exception {
        put("{\"preferences\":[{\"type\":\"REMINDER_2H\",\"email\":true}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='preferences[0].push')]").exists());

        verifyNoInteractions(service);
    }

    @Test
    void duplicatedTypesAreRejected() throws Exception {
        put("{\"preferences\":[{\"type\":\"REMINDER_2H\",\"email\":true,\"push\":true},"
                + "{\"type\":\"REMINDER_2H\",\"email\":false,\"push\":false}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='typesUnique')]").exists());

        verifyNoInteractions(service);
    }

    @Test
    void anEmptyOrMissingListIsRejected() throws Exception {
        put("{\"preferences\":[]}").andExpect(status().isBadRequest());
        put("{}").andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    @WithAnonymousUser
    void guestsGet401() throws Exception {
        mockMvc.perform(get("/api/me/notifications/preferences")).andExpect(status().isUnauthorized());
        put("{\"preferences\":[]}").andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    private ResultActions put(String body) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/me/notifications/preferences").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
