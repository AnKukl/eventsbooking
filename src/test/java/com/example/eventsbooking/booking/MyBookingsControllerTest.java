package com.example.eventsbooking.booking;

import com.example.eventsbooking.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MyBookingsController.class)
@Import(SecurityConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MyBookingsControllerTest {

    private final MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private BookingService service;

    MyBookingsControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    @WithMockUser(username = "7", roles = "USER")
    void returnsOnlyTheBookingsOfTheAuthenticatedUser() throws Exception {
        Booking booking = new Booking(5L, 7L);
        ReflectionTestUtils.setField(booking, "id", 100L);
        when(service.findMine(eq(7L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(booking)));

        mockMvc.perform(get("/api/bookings/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(100))
                .andExpect(jsonPath("$.content[0].userId").value(7))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "7", roles = "USER")
    void defaultOrderIsNewestFirst() throws Exception {
        when(service.findMine(eq(7L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/bookings/my")).andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(service).findMine(eq(7L), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getSort().toString())
                .isEqualTo("createdAt: DESC");
    }

    @Test
    @WithMockUser(username = "7", roles = "USER")
    void unknownSortPropertyReturns400() throws Exception {
        mockMvc.perform(get("/api/bookings/my").param("sort", "userId"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    void anonymousGets401() throws Exception {
        mockMvc.perform(get("/api/bookings/my")).andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }
}
