package com.example.eventsbooking.event;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class EventControllerTest {

    private static final String FUTURE = Instant.now().plus(7, ChronoUnit.DAYS).toString();
    private static final String PAST = Instant.now().minus(1, ChronoUnit.DAYS).toString();

    private final MockMvc mockMvc;

    @MockitoBean
    private EventService service;

    EventControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    // ---------- happy paths ----------

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        when(service.create(any(EventRequest.class))).thenReturn(event(5L));

        mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON)
                        .content(json("Jazz night", FUTURE, 50, 7L)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/events/5")))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.title").value("Jazz night"))
                .andExpect(jsonPath("$.capacity").value(50))
                .andExpect(jsonPath("$.organizerId").value(7));
    }

    @Test
    void getReturnsEvent() throws Exception {
        when(service.get(5L)).thenReturn(event(5L));

        mockMvc.perform(get("/api/events/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.title").value("Jazz night"));
    }

    @Test
    void listPassesPageableToServiceAndReturnsPage() throws Exception {
        when(service.list(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event(1L), event(2L)), PageRequest.of(0, 10), 2));

        mockMvc.perform(get("/api/events").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(2));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).list(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    void updateReturnsUpdatedEvent() throws Exception {
        when(service.update(eq(5L), any(EventRequest.class))).thenReturn(event(5L));

        mockMvc.perform(put("/api/events/5").contentType(MediaType.APPLICATION_JSON)
                        .content(json("Jazz night", FUTURE, 50, 7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/events/5")).andExpect(status().isNoContent());

        verify(service).delete(5L);
    }

    // ---------- validation (400, ProblemDetail with field errors) ----------

    @Test
    void createRejectsBlankTitle() throws Exception {
        assertValidationError(create(json("  ", FUTURE, 50, 7L)), "title");
    }

    @Test
    void createRejectsMissingTitle() throws Exception {
        assertValidationError(create(json(null, FUTURE, 50, 7L)), "title");
    }

    @Test
    void createRejectsTitleLongerThan200Characters() throws Exception {
        assertValidationError(create(json("x".repeat(201), FUTURE, 50, 7L)), "title");
    }

    @Test
    void createRejectsZeroCapacity() throws Exception {
        assertValidationError(create(json("Jazz night", FUTURE, 0, 7L)), "capacity");
    }

    @Test
    void createRejectsNegativeCapacity() throws Exception {
        assertValidationError(create(json("Jazz night", FUTURE, -5, 7L)), "capacity");
    }

    @Test
    void createRejectsMissingCapacity() throws Exception {
        assertValidationError(create(json("Jazz night", FUTURE, null, 7L)), "capacity");
    }

    @Test
    void createRejectsStartsAtInThePast() throws Exception {
        assertValidationError(create(json("Jazz night", PAST, 50, 7L)), "startsAt");
    }

    @Test
    void createRejectsMissingStartsAt() throws Exception {
        assertValidationError(create(json("Jazz night", null, 50, 7L)), "startsAt");
    }

    @Test
    void createRejectsMissingOrganizerId() throws Exception {
        assertValidationError(create(json("Jazz night", FUTURE, 50, null)), "organizerId");
    }

    @Test
    void createReportsAllInvalidFieldsAtOnce() throws Exception {
        create(json("", PAST, 0, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(4));
    }

    @Test
    void updateRejectsInvalidCapacity() throws Exception {
        mockMvc.perform(put("/api/events/5").contentType(MediaType.APPLICATION_JSON)
                        .content(json("Jazz night", FUTURE, 0, 7L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='capacity')]").exists());

        verifyNoInteractions(service);
    }

    // ---------- other errors (ProblemDetail, RFC 9457) ----------

    @Test
    void malformedJsonReturns400ProblemDetail() throws Exception {
        create("{not json")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(service);
    }

    @Test
    void nonNumericIdReturns400ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/events/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void missingEventReturns404ProblemDetail() throws Exception {
        when(service.get(99L)).thenThrow(new EventNotFoundException(99L));

        mockMvc.perform(get("/api/events/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Event not found"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("99")));
    }

    @Test
    void unexpectedExceptionReturns500ProblemDetailWithoutStackTrace() throws Exception {
        when(service.get(1L)).thenThrow(new IllegalStateException("secret internals"));

        mockMvc.perform(get("/api/events/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret internals"))));
    }

    // ---------- helpers ----------

    private ResultActions create(String body) throws Exception {
        return mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void assertValidationError(ResultActions result, String field) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[?(@.field=='" + field + "')]").exists());

        verifyNoInteractions(service);
    }

    private static Event event(long id) {
        Event event = new Event("Jazz night", "Live music", Instant.parse(FUTURE), 50, 7L);
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    private static String json(String title, String startsAt, Integer capacity, Long organizerId) {
        return """
                {"title": %s, "description": "Live music", "startsAt": %s, "capacity": %s, "organizerId": %s}
                """.formatted(quote(title), quote(startsAt), capacity, organizerId);
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
