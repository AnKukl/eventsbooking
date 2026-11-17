package com.example.eventsbooking.booking;

import com.example.eventsbooking.event.EventFullException;
import com.example.eventsbooking.event.EventNotFoundException;
import com.example.eventsbooking.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "7", roles = "USER")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class BookingControllerTest {

    private final MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private BookingService service;

    BookingControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void bookReturns201WithBooking() throws Exception {
        Booking booking = new Booking(5L, 7L);
        ReflectionTestUtils.setField(booking, "id", 100L);
        when(service.book(5L, 7L)).thenReturn(booking);

        book(5, "{\"userId\": 7}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.eventId").value(5))
                .andExpect(jsonPath("$.userId").value(7));
    }

    @Test
    void bookUsesTheAuthenticatedUserNotTheOneInTheBody() throws Exception {
        Booking booking = new Booking(5L, 7L);
        ReflectionTestUtils.setField(booking, "id", 100L);
        when(service.book(5L, 7L)).thenReturn(booking);

        mockMvc.perform(post("/api/events/5/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 99}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(7));

        verify(service).book(5L, 7L);
        verify(service, org.mockito.Mockito.never()).book(5L, 99L);
    }

    @Test
    void bookWorksWithoutAnyBody() throws Exception {
        Booking booking = new Booking(5L, 7L);
        ReflectionTestUtils.setField(booking, "id", 101L);
        when(service.book(5L, 7L)).thenReturn(booking);

        mockMvc.perform(post("/api/events/5/bookings")).andExpect(status().isCreated());
    }

    @Test
    void startedEventReturns409ProblemDetail() throws Exception {
        when(service.book(5L, 7L)).thenThrow(new EventAlreadyStartedException(5L));

        mockMvc.perform(post("/api/events/5/bookings"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Event already started"));
    }

    @Test
    void fullEventReturns409ProblemDetail() throws Exception {
        when(service.book(5L, 7L)).thenThrow(new EventFullException(5L));

        book(5, "{\"userId\": 7}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Event is full"))
                .andExpect(jsonPath("$.detail").value(containsString("5")));
    }

    @Test
    void duplicateBookingReturns409ProblemDetail() throws Exception {
        when(service.book(5L, 7L)).thenThrow(new AlreadyBookedException(5L, 7L));

        book(5, "{\"userId\": 7}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Already booked"));
    }

    @Test
    void exhaustedRetriesReturn409ProblemDetail() throws Exception {
        when(service.book(5L, 7L)).thenThrow(new ConcurrentBookingException(5L));

        book(5, "{\"userId\": 7}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Concurrent booking conflict"));
    }

    @Test
    void unknownEventReturns404ProblemDetail() throws Exception {
        when(service.book(99L, 7L)).thenThrow(new EventNotFoundException(99L));

        book(99, "{\"userId\": 7}")
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void nonNumericEventIdReturns400ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/events/abc/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 7}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void cancelReturns200WithCancelledBooking() throws Exception {
        Booking booking = new Booking(5L, 7L);
        ReflectionTestUtils.setField(booking, "id", 100L);
        booking.cancel(java.time.Instant.parse("2030-01-01T10:00:00Z"));
        when(service.cancel(5L, 100L, 7L)).thenReturn(booking);

        mockMvc.perform(post("/api/events/5/bookings/100/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").value("2030-01-01T10:00:00Z"));
    }

    @Test
    void tooLateCancellationReturns409ProblemDetail() throws Exception {
        when(service.cancel(5L, 100L, 7L)).thenThrow(new CancellationTooLateException(java.time.Duration.ofHours(24)));

        mockMvc.perform(post("/api/events/5/bookings/100/cancel"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Cancellation not allowed"))
                .andExpect(jsonPath("$.detail").value(containsString("24 hours")));
    }

    @Test
    void alreadyCancelledBookingReturns409ProblemDetail() throws Exception {
        when(service.cancel(5L, 100L, 7L)).thenThrow(new BookingAlreadyCancelledException(100L));

        mockMvc.perform(post("/api/events/5/bookings/100/cancel"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Already cancelled"));
    }

    @Test
    void cancellingUnknownBookingReturns404ProblemDetail() throws Exception {
        when(service.cancel(5L, 100L, 7L)).thenThrow(new BookingNotFoundException(100L));

        mockMvc.perform(post("/api/events/5/bookings/100/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Booking not found"));
    }

    @Test
    void nonNumericBookingIdReturns400ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/events/5/bookings/abc/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private org.springframework.test.web.servlet.ResultActions book(long eventId, String body) throws Exception {
        return mockMvc.perform(post("/api/events/" + eventId + "/bookings")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
