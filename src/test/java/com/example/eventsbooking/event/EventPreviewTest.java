package com.example.eventsbooking.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventPreviewTest {

    private static Event newEvent() {
        return new Event("Jazz", "d", Instant.parse("2099-01-01T00:00:00Z"), 10, 1L);
    }

    private static EventPreviewResponse preview(String shortDescription, List<String> fit, List<String> notFit,
            List<MediaResponse> media) {
        return new EventPreviewResponse(5L, shortDescription, fit, notFit, media);
    }

    // ---------- Event.applyPreview ----------

    @Test
    void newEventHasAnEmptyPreview() {
        Event event = newEvent();

        assertThat(event.getShortDescription()).isEmpty();
        assertThat(event.getAudienceFit()).isEmpty();
        assertThat(event.getAudienceNotFit()).isEmpty();
        assertThat(event.getMedia()).isEmpty();
    }

    @Test
    void applyPreviewStoresValuesInOrder() {
        Event event = newEvent();

        event.applyPreview("Short", List.of("a", "b"), List.of("c"),
                List.of(new EventMedia("https://img/1.png", "cover"), new EventMedia("https://img/2.png", null)));

        assertThat(event.getShortDescription()).isEqualTo("Short");
        assertThat(event.getAudienceFit()).containsExactly("a", "b");
        assertThat(event.getAudienceNotFit()).containsExactly("c");
        assertThat(event.getMedia()).extracting(EventMedia::url).containsExactly("https://img/1.png", "https://img/2.png");
    }

    @Test
    void nullMeansKeepWhatIsThere() {
        Event event = newEvent();
        event.applyPreview("Short", List.of("a"), List.of("c"), List.of(new EventMedia("https://img/1.png", null)));

        event.applyPreview(null, null, null, null);

        assertThat(event.getShortDescription()).isEqualTo("Short");
        assertThat(event.getAudienceFit()).containsExactly("a");
        assertThat(event.getAudienceNotFit()).containsExactly("c");
        assertThat(event.getMedia()).hasSize(1);
    }

    @Test
    void emptyValuesClearAndNewValuesReplace() {
        Event event = newEvent();
        event.applyPreview("Short", List.of("a", "b", "c"), List.of("x"), List.of(new EventMedia("https://img/1.png", null)));

        event.applyPreview("", List.of("z"), List.of(), List.of());

        assertThat(event.getShortDescription()).isEmpty();
        assertThat(event.getAudienceFit()).containsExactly("z");
        assertThat(event.getAudienceNotFit()).isEmpty();
        assertThat(event.getMedia()).isEmpty();
    }

    // ---------- EventPreviewResponse.etag ----------

    @Test
    void etagIsAQuotedStableHashOfTheContent() {
        EventPreviewResponse a = preview("S", List.of("a"), List.of(), List.of(new MediaResponse("https://i/1", null)));
        EventPreviewResponse same = preview("S", List.of("a"), List.of(), List.of(new MediaResponse("https://i/1", null)));

        assertThat(a.etag()).matches("\"[0-9a-f]{32}\"").isEqualTo(same.etag());
    }

    @Test
    void etagChangesWithAnyPreviewField() {
        EventPreviewResponse base = preview("S", List.of("a"), List.of("n"), List.of(new MediaResponse("https://i/1", "x")));

        assertThat(preview("S2", List.of("a"), List.of("n"), List.of(new MediaResponse("https://i/1", "x"))).etag())
                .isNotEqualTo(base.etag());
        assertThat(preview("S", List.of("b"), List.of("n"), List.of(new MediaResponse("https://i/1", "x"))).etag())
                .isNotEqualTo(base.etag());
        assertThat(preview("S", List.of("a"), List.of("m"), List.of(new MediaResponse("https://i/1", "x"))).etag())
                .isNotEqualTo(base.etag());
        assertThat(preview("S", List.of("a"), List.of("n"), List.of(new MediaResponse("https://i/1", "y"))).etag())
                .isNotEqualTo(base.etag());
        assertThat(new EventPreviewResponse(6L, "S", List.of("a"), List.of("n"),
                List.of(new MediaResponse("https://i/1", "x"))).etag()).isNotEqualTo(base.etag());
    }

    @Test
    void etagDistinguishesListSplitsThatLookAlikeWhenJoined() {
        assertThat(preview("S", List.of("a", "b"), List.of(), List.of()).etag())
                .isNotEqualTo(preview("S", List.of("a, b"), List.of(), List.of()).etag());
        assertThat(preview("S", List.of("a"), List.of(), List.of()).etag())
                .isNotEqualTo(preview("S", List.of(), List.of("a"), List.of()).etag());
    }

    @Test
    void etagDependsOnTheOrderOfMedia() {
        MediaResponse one = new MediaResponse("https://i/1", null);
        MediaResponse two = new MediaResponse("https://i/2", null);

        assertThat(preview("S", List.of(), List.of(), List.of(one, two)).etag())
                .isNotEqualTo(preview("S", List.of(), List.of(), List.of(two, one)).etag());
    }
}
