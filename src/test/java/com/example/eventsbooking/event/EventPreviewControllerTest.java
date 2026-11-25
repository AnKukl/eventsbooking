package com.example.eventsbooking.event;

import com.example.eventsbooking.favorite.FavoriteService;
import com.example.eventsbooking.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Preview fields: limits (400 ProblemDetail) and the cache headers of GET /api/events/{id}/preview. */
@WebMvcTest(EventController.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "7", roles = "ORGANIZER")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class EventPreviewControllerTest {

    private static final String FUTURE = Instant.now().plus(7, ChronoUnit.DAYS).toString();

    private final MockMvc mockMvc;

    @MockitoBean

    private com.example.eventsbooking.event.EventCovers eventCovers;


    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private EventService service;

    @MockitoBean
    private FavoriteService favorites;

    EventPreviewControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    // ---------- GET /preview ----------

    @Test
    void previewReturnsOnlyThePreviewFields() throws Exception {
        when(service.preview(5L)).thenReturn(samplePreview());

        mockMvc.perform(get("/api/events/5/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.shortDescription").value("A short pitch"))
                .andExpect(jsonPath("$.audienceFit[0]").value("Beginners"))
                .andExpect(jsonPath("$.audienceNotFit.length()").value(0))
                .andExpect(jsonPath("$.media[0].url").value("https://img.example/1.png"))
                .andExpect(jsonPath("$.media[0].alt").value("Cover"))
                .andExpect(jsonPath("$.title").doesNotExist())
                .andExpect(jsonPath("$.capacity").doesNotExist())
                .andExpect(jsonPath("$.bookedCount").doesNotExist())
                .andExpect(jsonPath("$.organizerId").doesNotExist())
                .andExpect(jsonPath("$.isFavorite").doesNotExist());
    }

    @Test
    void previewCarriesCacheControlAndETag() throws Exception {
        when(service.preview(5L)).thenReturn(samplePreview());

        mockMvc.perform(get("/api/events/5/preview"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=300")))
                .andExpect(header().string(HttpHeaders.ETAG, matchesPattern("\"[0-9a-f]{32}\"")));
    }

    @Test
    void matchingIfNoneMatchGets304WithoutBodyButKeepsTheCacheHeaders() throws Exception {
        when(service.preview(5L)).thenReturn(samplePreview());
        String etag = mockMvc.perform(get("/api/events/5/preview")).andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        mockMvc.perform(get("/api/events/5/preview").header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(content().string(emptyString()))
                .andExpect(header().string(HttpHeaders.ETAG, etag))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=300")));
    }

    @Test
    void staleIfNoneMatchGetsTheFullResponse() throws Exception {
        when(service.preview(5L)).thenReturn(samplePreview());

        mockMvc.perform(get("/api/events/5/preview").header(HttpHeaders.IF_NONE_MATCH, "\"stale\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortDescription").value("A short pitch"));
    }

    @Test
    void eTagChangesWhenThePreviewChanges() throws Exception {
        when(service.preview(5L)).thenReturn(samplePreview());
        String before = mockMvc.perform(get("/api/events/5/preview")).andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        when(service.preview(5L)).thenReturn(new EventPreviewResponse(5L, "Edited pitch", List.of("Beginners"),
                List.of(), List.of(new MediaResponse("https://img.example/1.png", "Cover"))));

        mockMvc.perform(get("/api/events/5/preview").header(HttpHeaders.IF_NONE_MATCH, before))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, org.hamcrest.Matchers.not(before)));
    }

    @Test
    void previewOfAnUnknownEventIs404ProblemDetail() throws Exception {
        when(service.preview(99L)).thenThrow(new EventNotFoundException(99L));

        mockMvc.perform(get("/api/events/99/preview"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @WithAnonymousUser
    void guestsMayReadThePreview() throws Exception {
        when(service.preview(5L)).thenReturn(samplePreview());

        mockMvc.perform(get("/api/events/5/preview")).andExpect(status().isOk());
    }

    // ---------- limits: accepted at the boundary ----------

    @Test
    void maximumSizesAreAccepted() throws Exception {
        when(service.create(any(EventRequest.class), eq(7L))).thenReturn(event(5L));

        create(body("\"shortDescription\":\"" + "x".repeat(160) + "\""
                + ",\"audienceFit\":" + strings(4, 100)
                + ",\"audienceNotFit\":" + strings(3, 100)
                + ",\"media\":" + media(6)))
                .andExpect(status().isCreated());
    }

    @Test
    void notFitMayBeEmptyAndEverythingMayBeOmitted() throws Exception {
        when(service.create(any(EventRequest.class), eq(7L))).thenReturn(event(5L));

        create(body("\"audienceNotFit\":[]")).andExpect(status().isCreated());
        create(body("")).andExpect(status().isCreated());
    }

    @Test
    void previewFieldsReachTheService() throws Exception {
        when(service.create(any(EventRequest.class), eq(7L))).thenReturn(event(5L));

        create(body("\"shortDescription\":\"Pitch\",\"audienceFit\":[\"a\",\"b\"],\"audienceNotFit\":[\"c\"],"
                + "\"media\":[{\"url\":\"https://img.example/1.png\",\"alt\":\"Cover\"}]")).andExpect(status().isCreated());

        var captor = org.mockito.ArgumentCaptor.forClass(EventRequest.class);
        verify(service).create(captor.capture(), eq(7L));
        EventRequest request = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(request.shortDescription()).isEqualTo("Pitch");
        org.assertj.core.api.Assertions.assertThat(request.audienceFit()).containsExactly("a", "b");
        org.assertj.core.api.Assertions.assertThat(request.audienceNotFit()).containsExactly("c");
        org.assertj.core.api.Assertions.assertThat(request.media())
                .containsExactly(new MediaRequest("https://img.example/1.png", "Cover"));
    }

    // ---------- limits: rejected with 400 ProblemDetail ----------

    @Test
    void shortDescriptionOver160CharactersIsRejected() throws Exception {
        assertRejected(create(body("\"shortDescription\":\"" + "x".repeat(161) + "\"")), "shortDescription");
    }

    @Test
    void moreThanFourAudienceFitLinesAreRejected() throws Exception {
        assertRejected(create(body("\"audienceFit\":" + strings(5, 10))), "audienceFit");
    }

    @Test
    void moreThanThreeAudienceNotFitLinesAreRejected() throws Exception {
        assertRejected(create(body("\"audienceNotFit\":" + strings(4, 10))), "audienceNotFit");
    }

    @Test
    void moreThanSixImagesAreRejected() throws Exception {
        assertRejected(create(body("\"media\":" + media(7))), "media");
    }

    @Test
    void aBlankAudienceLineIsRejected() throws Exception {
        assertRejected(create(body("\"audienceFit\":[\"ok\",\"  \"]")), "audienceFit[1]");
    }

    @Test
    void anAudienceLineOver100CharactersIsRejected() throws Exception {
        assertRejected(create(body("\"audienceNotFit\":[\"" + "y".repeat(101) + "\"]")), "audienceNotFit[0]");
    }

    @Test
    void anImageUrlThatIsNotHttpIsRejected() throws Exception {
        assertRejected(create(body("\"media\":[{\"url\":\"javascript:alert(1)\"}]")), "media[0].url");
        assertRejected(create(body("\"media\":[{\"url\":\"\"}]")), "media[0].url");
    }

    @Test
    void anImageAltOver200CharactersIsRejected() throws Exception {
        assertRejected(create(body("\"media\":[{\"url\":\"https://img.example/1.png\",\"alt\":\""
                + "z".repeat(201) + "\"}]")), "media[0].alt");
    }

    @Test
    void theSameLimitsApplyToUpdate() throws Exception {
        mockMvc.perform(put("/api/events/5").contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"audienceFit\":" + strings(5, 10))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[?(@.field=='audienceFit')]").exists());

        verifyNoInteractions(service);
    }

    // ---------- helpers ----------

    private ResultActions create(String body) throws Exception {
        return mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void assertRejected(ResultActions result, String field) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[?(@.field=='" + field + "')]").exists());
        verifyNoInteractions(service);
    }

    private static String body(String extra) {
        return "{\"title\":\"Jazz night\",\"description\":\"d\",\"startsAt\":\"" + FUTURE + "\",\"capacity\":50"
                + (extra.isEmpty() ? "" : "," + extra) + "}";
    }

    private static String strings(int count, int length) {
        return IntStream.range(0, count).mapToObj(i -> "\"" + "s".repeat(length) + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String media(int count) {
        return IntStream.range(0, count).mapToObj(i -> "{\"url\":\"https://img.example/" + i + ".png\",\"alt\":\"a\"}")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static EventPreviewResponse samplePreview() {
        return new EventPreviewResponse(5L, "A short pitch", List.of("Beginners"), List.of(),
                List.of(new MediaResponse("https://img.example/1.png", "Cover")));
    }

    private static Event event(long id) {
        Event event = new Event("Jazz night", "d", Instant.parse("2099-01-01T00:00:00Z"), 50, 7L);
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }
}
