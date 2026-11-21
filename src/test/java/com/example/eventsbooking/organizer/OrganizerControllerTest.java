package com.example.eventsbooking.organizer;

import com.example.eventsbooking.booking.BookingStatus;
import com.example.eventsbooking.common.ConflictException;
import com.example.eventsbooking.event.EventMedia;
import com.example.eventsbooking.event.MediaResponse;
import com.example.eventsbooking.event.MissingField;
import com.example.eventsbooking.security.SecurityConfig;
import com.example.eventsbooking.support.TestImages;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {OrganizerEventController.class, OrganizerAnalyticsController.class})
@Import(SecurityConfig.class)
@WithMockUser(username = "7", roles = "ORGANIZER")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OrganizerControllerTest {

    private static final String FUTURE = Instant.now().plus(30, ChronoUnit.DAYS).toString();

    private final MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private OrganizerEventService events;

    @MockitoBean
    private EventMediaService media;

    @MockitoBean
    private OrganizerBookingsService bookings;

    @MockitoBean
    private OrganizerAnalyticsService analytics;

    OrganizerControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    // ---------- roles ----------

    @Test
    @WithMockUser(username = "7", roles = "USER")
    void anOrdinaryUserIsForbiddenEverywhereUnderOrganizer() throws Exception {
        mockMvc.perform(get("/api/organizer/events")).andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(get("/api/organizer/events/5")).andExpect(status().isForbidden());
        json(post("/api/organizer/events"), createBody()).andExpect(status().isForbidden());
        json(patch("/api/organizer/events/5"), "{\"version\":0}").andExpect(status().isForbidden());
        mockMvc.perform(put("/api/organizer/events/5/publish")).andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/organizer/events/5/media").file(png())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/organizer/events/5/bookings")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/organizer/analytics")).andExpect(status().isForbidden());

        verifyNoInteractions(events, media, bookings, analytics);
    }

    @Test
    @WithAnonymousUser
    void guestsGet401() throws Exception {
        mockMvc.perform(get("/api/organizer/events")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(get("/api/organizer/analytics")).andExpect(status().isUnauthorized());

        verifyNoInteractions(events, analytics);
    }

    // ---------- list ----------

    @Test
    void theListIsPaginatedAndUsesTheCallersId() throws Exception {
        when(events.list(eq(7L), eq(OrganizerEventStatus.SOLD_OUT), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response(5L, OrganizerEventStatus.SOLD_OUT)), PageRequest.of(1, 5), 6));

        mockMvc.perform(get("/api/organizer/events").param("status", "SOLD_OUT").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(5))
                .andExpect(jsonPath("$.content[0].status").value("SOLD_OUT"))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(6));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(events).list(eq(7L), eq(OrganizerEventStatus.SOLD_OUT), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void anUnknownStatusOrSortIsRejected() throws Exception {
        mockMvc.perform(get("/api/organizer/events").param("status", "ARCHIVED")).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(get("/api/organizer/events").param("sort", "version")).andExpect(status().isBadRequest());

        verifyNoInteractions(events);
    }

    // ---------- create / update ----------

    @Test
    void createReturns201WithLocationAndTheDraft() throws Exception {
        when(events.create(any(OrganizerEventCreateRequest.class), eq(7L)))
                .thenReturn(response(5L, OrganizerEventStatus.DRAFT));

        json(post("/api/organizer/events"), createBody())
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/organizer/events/5")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void createValidatesEveryField() throws Exception {
        assertRejected(json(post("/api/organizer/events"), "{\"startsAt\":\"" + FUTURE + "\",\"capacity\":5}"), "title");
        assertRejected(json(post("/api/organizer/events"), "{\"title\":\"  \",\"startsAt\":\"" + FUTURE + "\",\"capacity\":5}"), "title");
        assertRejected(json(post("/api/organizer/events"), "{\"title\":\"T\",\"capacity\":5}"), "startsAt");
        assertRejected(json(post("/api/organizer/events"), "{\"title\":\"T\",\"startsAt\":\"" + FUTURE + "\"}"), "capacity");
        assertRejected(json(post("/api/organizer/events"), "{\"title\":\"T\",\"startsAt\":\"" + FUTURE + "\",\"capacity\":0}"), "capacity");
        assertRejected(json(post("/api/organizer/events"), createBody("\"price\":-1")), "price");
        assertRejected(json(post("/api/organizer/events"), createBody("\"price\":123456789.00")), "price");
        assertRejected(json(post("/api/organizer/events"), createBody("\"price\":1.999")), "price");
        assertRejected(json(post("/api/organizer/events"), createBody("\"venue\":\"" + "v".repeat(201) + "\"")), "venue");
        assertRejected(json(post("/api/organizer/events"), createBody("\"shortDescription\":\"" + "s".repeat(161) + "\"")), "shortDescription");
        assertRejected(json(post("/api/organizer/events"), createBody("\"audienceFit\":[\"1\",\"2\",\"3\",\"4\",\"5\"]")), "audienceFit");
    }

    @Test
    void patchNeedsTheVersionAndRejectsABlankTitle() throws Exception {
        assertRejected(json(patch("/api/organizer/events/5"), "{\"title\":\"New\"}"), "version");
        assertRejected(json(patch("/api/organizer/events/5"), "{\"version\":0,\"title\":\"  \"}"), "title");
        assertRejected(json(patch("/api/organizer/events/5"), "{\"version\":0,\"capacity\":0}"), "capacity");
    }

    @Test
    void patchPassesTheFieldsTheEventAndTheCallerToTheService() throws Exception {
        when(events.update(eq(5L), any(OrganizerEventUpdateRequest.class), eq(7L)))
                .thenReturn(response(5L, OrganizerEventStatus.DRAFT));

        json(patch("/api/organizer/events/5"), "{\"version\":3,\"title\":\"New\",\"waitlistEnabled\":false}")
                .andExpect(status().isOk());

        ArgumentCaptor<OrganizerEventUpdateRequest> request = ArgumentCaptor.forClass(OrganizerEventUpdateRequest.class);
        verify(events).update(eq(5L), request.capture(), eq(7L));
        assertThat(request.getValue().version()).isEqualTo(3L);
        assertThat(request.getValue().title()).isEqualTo("New");
        assertThat(request.getValue().waitlistEnabled()).isFalse();
        assertThat(request.getValue().capacity()).isNull();
    }

    @Test
    void aStaleVersionIs409ProblemDetail() throws Exception {
        when(events.update(eq(5L), any(), eq(7L))).thenThrow(new StaleEventVersionException(5L));

        json(patch("/api/organizer/events/5"), "{\"version\":0,\"title\":\"X\"}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void someoneElsesEventIs403ProblemDetail() throws Exception {
        when(events.get(5L, 7L)).thenThrow(new AccessDeniedException("not yours"));

        mockMvc.perform(get("/api/organizer/events/5")).andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    // ---------- publish ----------

    @Test
    void publishReturnsThePublishedEvent() throws Exception {
        when(events.publish(5L, 7L)).thenReturn(response(5L, OrganizerEventStatus.PUBLISHED));

        mockMvc.perform(put("/api/organizer/events/5/publish")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void publishingAnIncompleteEventIs422WithTheListOfMissingFields() throws Exception {
        when(events.publish(5L, 7L)).thenThrow(new EventNotPublishableException(
                List.of(new MissingField("venue", "must be set"), new MissingField("media", "at least one photo is required"))));

        mockMvc.perform(put("/api/organizer/events/5/publish"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("venue"))
                .andExpect(jsonPath("$.errors[1].field").value("media"));
    }

    // ---------- media ----------

    @Test
    void uploadedFilesGoToTheServiceAndTheNewUrlsComeBack() throws Exception {
        when(media.upload(eq(5L), eq(7L), any())).thenReturn(List.of(new MediaResponse("/api/media/a.png", null)));

        mockMvc.perform(multipart("/api/organizer/events/5/media").file(png()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].url").value("/api/media/a.png"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UploadedImage>> files = ArgumentCaptor.forClass(List.class);
        verify(media).upload(eq(5L), eq(7L), files.capture());
        assertThat(files.getValue()).hasSize(1);
        assertThat(files.getValue().get(0).filename()).isEqualTo("cover.png");
        assertThat(files.getValue().get(0).content()).isEqualTo(TestImages.png());
    }

    @Test
    void severalFilesInOneRequestAreAllPassedOn() throws Exception {
        when(media.upload(eq(5L), eq(7L), any())).thenReturn(List.of());

        mockMvc.perform(multipart("/api/organizer/events/5/media").file(png()).file(png())).andExpect(status().isCreated());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UploadedImage>> files = ArgumentCaptor.forClass(List.class);
        verify(media).upload(eq(5L), eq(7L), files.capture());
        assertThat(files.getValue()).hasSize(2);
    }

    @Test
    void aRequestWithoutFilesIs400() throws Exception {
        mockMvc.perform(multipart("/api/organizer/events/5/media")).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(media);
    }

    @Test
    void rejectedImagesMapToProblemDetails() throws Exception {
        when(media.upload(eq(5L), eq(7L), any())).thenThrow(new UnsupportedImageTypeException("does not match"));
        mockMvc.perform(multipart("/api/organizer/events/5/media").file(png())).andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        when(media.upload(eq(5L), eq(7L), any())).thenThrow(new ImageTooLargeException());
        mockMvc.perform(multipart("/api/organizer/events/5/media").file(png())).andExpect(status().isContentTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        when(media.upload(eq(5L), eq(7L), any())).thenThrow(new InvalidImageException("empty"));
        mockMvc.perform(multipart("/api/organizer/events/5/media").file(png())).andExpect(status().isBadRequest());

        when(media.upload(eq(5L), eq(7L), any())).thenThrow(new ConflictException("Too many images", "at most 6"));
        mockMvc.perform(multipart("/api/organizer/events/5/media").file(png())).andExpect(status().isConflict());
    }

    // ---------- bookings of an event ----------

    @Test
    void theBookingsListShowsNoContactDetails() throws Exception {
        when(bookings.list(eq(5L), eq(7L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(
                new OrganizerBookingResponse(11L, BookingStatus.CONFIRMED, 2, Instant.parse("2030-01-01T10:00:00Z"), null,
                        null, new OrganizerBookingResponse.Attendee(42L, "Anna"))), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/organizer/events/5/bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(11))
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.content[0].seats").value(2))
                .andExpect(jsonPath("$.content[0].attendee.id").value(42))
                .andExpect(jsonPath("$.content[0].attendee.name").value("Anna"))
                .andExpect(jsonPath("$.content[0].attendee.email").doesNotExist())
                .andExpect(jsonPath("$.content[0].attendee.phone").doesNotExist())
                .andExpect(jsonPath("$.content[0].userId").doesNotExist())
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void theBookingsOfSomeoneElsesEventAreForbidden() throws Exception {
        when(bookings.list(eq(5L), eq(7L), any(Pageable.class))).thenThrow(new AccessDeniedException("not yours"));

        mockMvc.perform(get("/api/organizer/events/5/bookings")).andExpect(status().isForbidden());
    }

    // ---------- analytics ----------

    @Test
    void analyticsDefaultsToSevenDaysAndUsesTheCaller() throws Exception {
        when(analytics.analytics(7L, "7d")).thenReturn(new AnalyticsResponse("7d", Instant.parse("2030-02-22T12:00:00Z"),
                Instant.parse("2030-03-01T12:00:00Z"), 12, new BigDecimal("240.00"), new BigDecimal("0.2500")));

        mockMvc.perform(get("/api/organizer/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("7d"))
                .andExpect(jsonPath("$.sold").value(12))
                .andExpect(jsonPath("$.revenue").value(240.00))
                .andExpect(jsonPath("$.occupancy").value(0.25));
    }

    @Test
    void anInvalidRangeIs400ProblemDetail() throws Exception {
        for (String range : new String[] {"abc", "7", "7days", "-1d", "0d"}) {
            mockMvc.perform(get("/api/organizer/analytics").param("range", range)).andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        verifyNoInteractions(analytics);

        when(analytics.analytics(7L, "999d")).thenThrow(new InvalidRangeException());
        mockMvc.perform(get("/api/organizer/analytics").param("range", "999d")).andExpect(status().isBadRequest());
    }

    // ---------- helpers ----------

    private ResultActions json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String body) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void assertRejected(ResultActions result, String field) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[?(@.field=='" + field + "')]").exists());
        verifyNoInteractions(events);
    }

    private static String createBody(String... extra) {
        return "{\"title\":\"Jazz night\",\"startsAt\":\"" + FUTURE + "\",\"capacity\":50"
                + (extra.length == 0 ? "" : "," + String.join(",", extra)) + "}";
    }

    private static MockMultipartFile png() {
        return new MockMultipartFile("files", "cover.png", "image/png", TestImages.png());
    }

    private static OrganizerEventResponse response(long id, OrganizerEventStatus status) {
        return new OrganizerEventResponse(id, "Jazz night", null, null, Instant.parse(FUTURE), 50, 0, null, null, true,
                status, 0L, List.of(), List.of(), List.of());
    }
}
