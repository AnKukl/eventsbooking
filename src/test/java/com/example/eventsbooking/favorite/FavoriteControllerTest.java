package com.example.eventsbooking.favorite;

import com.example.eventsbooking.event.Event;
import com.example.eventsbooking.event.EventNotFoundException;
import com.example.eventsbooking.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FavoriteController.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "7")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FavoriteControllerTest {

    private final MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private FavoriteService service;

    FavoriteControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    // ---------- POST ----------

    @Test
    void firstPostReturns201() throws Exception {
        when(service.add(7L, 5L)).thenReturn(true);

        mockMvc.perform(post("/api/favorites/5")).andExpect(status().isCreated());
    }

    @Test
    void repeatedPostReturns200() throws Exception {
        when(service.add(7L, 5L)).thenReturn(false);

        mockMvc.perform(post("/api/favorites/5")).andExpect(status().isOk());
    }

    @Test
    void postForAnUnknownEventReturns404ProblemDetail() throws Exception {
        when(service.add(7L, 99L)).thenThrow(new EventNotFoundException(99L));

        mockMvc.perform(post("/api/favorites/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    // ---------- DELETE ----------

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/favorites/5")).andExpect(status().isNoContent());

        verify(service).remove(7L, 5L);
    }

    // ---------- GET ----------

    @Test
    void listReturnsPageOfEventsMarkedAsFavoriteWithSeatStatus() throws Exception {
        Event full = event(1L, 1);
        full.book();
        Event free = event(2L, 10);
        when(service.list(org.mockito.ArgumentMatchers.eq(7L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(full, free), PageRequest.of(0, 10), 2));

        mockMvc.perform(get("/api/favorites").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].seatStatus").value("SOLD_OUT"))
                .andExpect(jsonPath("$.content[0].isFavorite").value(true))
                .andExpect(jsonPath("$.content[1].seatStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.content[1].isFavorite").value(true))
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void listPassesTheUserFromTheTokenAndThePageable() throws Exception {
        when(service.list(org.mockito.ArgumentMatchers.eq(7L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/favorites").param("page", "2").param("size", "5"))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(service).list(org.mockito.ArgumentMatchers.eq(7L), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void listRejectsSortBecauseTheOrderIsFixed() throws Exception {
        mockMvc.perform(get("/api/favorites").param("sort", "version"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    // ---------- guests: 401 ProblemDetail ----------

    @Test
    @WithAnonymousUser
    void guestPostGets401ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/favorites/5"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));

        verifyNoInteractions(service);
    }

    @Test
    @WithAnonymousUser
    void guestDeleteGets401ProblemDetail() throws Exception {
        mockMvc.perform(delete("/api/favorites/5"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    @WithAnonymousUser
    void guestListGets401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/favorites"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    private static Event event(long id, int capacity) {
        Event event = new Event("Jazz night", "d", Instant.parse("2099-01-01T00:00:00Z"), capacity, 1L);
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }
}
