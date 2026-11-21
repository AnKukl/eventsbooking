package com.example.eventsbooking.booking;

import com.example.eventsbooking.event.EventFullException;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "7")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class BookingControllerTest {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    private final MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private BookingService service;

    BookingControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    // ---------- POST /api/bookings ----------

    @Test
    void bookingCreatesAHoldForTheUserFromTheToken() throws Exception {
        when(service.book(5L, 7L, 2, null)).thenReturn(new BookingOutcome(hold(50L, 2), true));

        book("{\"eventId\":5,\"seats\":2}", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.eventId").value(5))
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.seats").value(2))
                .andExpect(jsonPath("$.status").value("HOLD"))
                .andExpect(jsonPath("$.holdExpiresAt").exists());
    }

    @Test
    void seatsDefaultToOne() throws Exception {
        when(service.book(5L, 7L, 1, null)).thenReturn(new BookingOutcome(hold(50L, 1), true));

        book("{\"eventId\":5}", null).andExpect(status().isCreated());

        verify(service).book(5L, 7L, 1, null);
    }

    @Test
    void aUserIdInTheBodyIsIgnored() throws Exception {
        when(service.book(5L, 7L, 1, null)).thenReturn(new BookingOutcome(hold(50L, 1), true));

        book("{\"eventId\":5,\"userId\":99}", null).andExpect(status().isCreated());

        verify(service).book(5L, 7L, 1, null);
    }

    @Test
    void theIdempotencyKeyReachesTheServiceAndAReplayIs200WithAMarker() throws Exception {
        when(service.book(5L, 7L, 1, "key-123")).thenReturn(new BookingOutcome(hold(50L, 1), false));

        book("{\"eventId\":5}", "key-123")
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(50));
    }

    @Test
    void aMalformedIdempotencyKeyIsRejected() throws Exception {
        for (String key : new String[] {"has space", "x".repeat(101), "tab\tkey", "<script>"}) {
            book("{\"eventId\":5}", key)
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        verifyNoInteractions(service);
    }

    @Test
    void theBodyIsValidated() throws Exception {
        for (String body : new String[] {"{}", "{\"eventId\":0}", "{\"eventId\":-1}", "{\"eventId\":5,\"seats\":0}",
                "{\"eventId\":5,\"seats\":-2}"}) {
            book(body, null).andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errors").isNotEmpty());
        }
        book("{\"eventId\":\"abc\"}", null).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void tooManySeatsIs400ProblemDetail() throws Exception {
        when(service.book(5L, 7L, 9, null)).thenThrow(new InvalidSeatCountException(4));

        book("{\"eventId\":5,\"seats\":9}", null)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid seat count"));
    }

    @Test
    void businessConflictsAre409ProblemDetails() throws Exception {
        when(service.book(5L, 7L, 1, null)).thenThrow(new EventFullException(5L));
        book("{\"eventId\":5}", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Event is full"));

        when(service.book(6L, 7L, 1, null)).thenThrow(new AlreadyBookedException(6L, 7L));
        book("{\"eventId\":6}", null).andExpect(status().isConflict());

        when(service.book(8L, 7L, 1, "k")).thenThrow(new IdempotencyKeyReusedException());
        book("{\"eventId\":8}", "k").andExpect(status().isConflict());

        when(service.book(9L, 7L, 1, null)).thenThrow(new ConcurrentBookingException(9L));
        book("{\"eventId\":9}", null).andExpect(status().isConflict());
    }

    @Test
    void anUnknownEventIs404() throws Exception {
        when(service.book(99L, 7L, 1, null)).thenThrow(new EventNotFoundException(99L));

        book("{\"eventId\":99}", null).andExpect(status().isNotFound());
    }

    // ---------- POST /api/bookings/{id}/confirm ----------

    @Test
    void confirmReturnsTheConfirmedBooking() throws Exception {
        Booking booking = hold(50L, 1);
        booking.confirm(NOW);
        when(service.confirm(50L, 7L)).thenReturn(booking);

        mockMvc.perform(post("/api/bookings/50/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void anExpiredHoldCannotBeConfirmed() throws Exception {
        when(service.confirm(50L, 7L)).thenThrow(new HoldExpiredException());

        mockMvc.perform(post("/api/bookings/50/confirm")).andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // ---------- POST /api/bookings/{id}/cancel ----------

    @Test
    void cancelPassesTheReasonAndTheUserFromTheToken() throws Exception {
        Booking booking = hold(50L, 1);
        booking.confirm(NOW);
        booking.cancel(NOW, "CHANGED_PLANS", true);
        when(service.cancel(50L, 7L, CancellationReason.CHANGED_PLANS)).thenReturn(booking);

        cancel(50, "{\"reason\":\"CHANGED_PLANS\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUND_PENDING"))
                .andExpect(jsonPath("$.cancellationReason").value("CHANGED_PLANS"))
                .andExpect(jsonPath("$.cancelledAt").exists());
    }

    @Test
    void theReasonIsRequiredAndMustBeOneOfTheKnownOnes() throws Exception {
        cancel(50, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='reason')]").exists());
        cancel(50, "{\"reason\":\"BECAUSE\"}").andExpect(status().isBadRequest());
        cancel(50, "{\"reason\":\"HOLD_EXPIRED\"}").andExpect(status().isBadRequest());   // system reason only
        mockMvc.perform(post("/api/bookings/50/cancel")).andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void cancellingTwiceIs409AndSomeoneElsesBookingIs404() throws Exception {
        when(service.cancel(eq(50L), eq(7L), any())).thenThrow(new BookingAlreadyCancelledException(50L));
        cancel(50, "{\"reason\":\"OTHER\"}").andExpect(status().isConflict());

        when(service.cancel(eq(51L), eq(7L), any())).thenThrow(new BookingNotFoundException(51L));
        cancel(51, "{\"reason\":\"OTHER\"}").andExpect(status().isNotFound());
    }

    // ---------- guests ----------

    @Test
    @WithAnonymousUser
    void guestsGet401ProblemDetailOnEveryEndpoint() throws Exception {
        book("{\"eventId\":5}", null).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(post("/api/bookings/50/confirm")).andExpect(status().isUnauthorized());
        cancel(50, "{\"reason\":\"OTHER\"}").andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    // ---------- helpers ----------

    private ResultActions book(String body, String idempotencyKey) throws Exception {
        var request = post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(body);
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request);
    }

    private ResultActions cancel(long id, String body) throws Exception {
        return mockMvc.perform(post("/api/bookings/" + id + "/cancel").contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static Booking hold(long id, int seats) {
        Booking booking = new Booking(5L, 7L, seats, NOW, NOW.plusSeconds(600));
        ReflectionTestUtils.setField(booking, "id", id);
        return booking;
    }
}
