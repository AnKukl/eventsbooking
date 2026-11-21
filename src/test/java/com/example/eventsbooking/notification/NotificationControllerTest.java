package com.example.eventsbooking.notification;

import com.example.eventsbooking.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
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

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "7")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class NotificationControllerTest {

    private final MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private NotificationService service;

    NotificationControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void returnsAPageOfTheCallersNotifications() throws Exception {
        when(service.feed(eq(7L), any(Pageable.class))).thenReturn(new PageImpl<>(
                List.of(notification(2L, "Seat available"), notification(1L, "Booking confirmed")),
                PageRequest.of(0, 10), 2));

        mockMvc.perform(get("/api/me/notifications").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(2))
                .andExpect(jsonPath("$.content[0].type").value("SEAT_AVAILABLE"))
                .andExpect(jsonPath("$.content[0].title").value("Seat available"))
                .andExpect(jsonPath("$.content[0].body").value("Body text"))
                .andExpect(jsonPath("$.content[0].targetEventId").value(5))
                .andExpect(jsonPath("$.content[0].readAt").doesNotExist())
                .andExpect(jsonPath("$.content[0].createdAt").exists())
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void passesTheUserFromTheTokenAndThePageable() throws Exception {
        when(service.feed(eq(7L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/me/notifications").param("page", "3").param("size", "5"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).feed(eq(7L), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(3);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void theOrderIsFixedSoSortIsRejected() throws Exception {
        mockMvc.perform(get("/api/me/notifications").param("sort", "readAt"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    @WithAnonymousUser
    void guestsGet401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/me/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    private static Notification notification(long id, String title) {
        Notification notification = new Notification();
        ReflectionTestUtils.setField(notification, "id", id);
        ReflectionTestUtils.setField(notification, "userId", 7L);
        ReflectionTestUtils.setField(notification, "type", "SEAT_AVAILABLE");
        ReflectionTestUtils.setField(notification, "title", title);
        ReflectionTestUtils.setField(notification, "body", "Body text");
        ReflectionTestUtils.setField(notification, "targetEventId", 5L);
        ReflectionTestUtils.setField(notification, "createdAt", Instant.parse("2030-01-01T10:00:00Z"));
        return notification;
    }
}
