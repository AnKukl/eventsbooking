package com.example.eventsbooking.booking;

import com.example.eventsbooking.event.EventFullException;
import com.example.eventsbooking.event.EventNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class BookingControllerTest {

    private final MockMvc mockMvc;

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
    void bookRejectsMissingUserId() throws Exception {
        book(5, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[?(@.field=='userId')]").exists());

        verifyNoInteractions(service);
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

    private org.springframework.test.web.servlet.ResultActions book(long eventId, String body) throws Exception {
        return mockMvc.perform(post("/api/events/" + eventId + "/bookings")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
