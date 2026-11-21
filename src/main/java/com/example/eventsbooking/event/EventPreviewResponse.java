package com.example.eventsbooking.event;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Only the preview fields of an event (plus its id). The first image of {@code media} is the cover. */
public record EventPreviewResponse(
        Long id,
        String shortDescription,
        List<String> audienceFit,
        List<String> audienceNotFit,
        List<MediaResponse> media) {

    /** Built inside the service transaction: the lists are lazy. */
    static EventPreviewResponse from(Event event) {
        return new EventPreviewResponse(event.getId(), event.getShortDescription(), event.getAudienceFit(),
                event.getAudienceNotFit(), event.getMedia().stream()
                        .map(media -> new MediaResponse(media.url(), media.alt())).toList());
    }

    /**
     * Strong ETag derived from the content only, so it changes exactly when the preview changes (a booking does not
     * touch it). Every string and list is length-prefixed, so different lists can never hash alike
     * (["a", "b"] versus ["a, b"]).
     */
    public String etag() {
        MessageDigest digest = sha256();
        add(digest, String.valueOf(id));
        add(digest, shortDescription);
        addAll(digest, audienceFit);
        addAll(digest, audienceNotFit);
        add(digest, String.valueOf(media.size()));
        for (MediaResponse item : media) {
            add(digest, item.url());
            add(digest, item.alt());
        }
        return "\"" + HexFormat.of().formatHex(digest.digest(), 0, 16) + "\"";
    }

    private static void addAll(MessageDigest digest, List<String> values) {
        add(digest, String.valueOf(values.size()));
        values.forEach(value -> add(digest, value));
    }

    private static void add(MessageDigest digest, String value) {
        if (value == null) {
            digest.update((byte) 0);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) 1);
        digest.update(String.valueOf(bytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }
}
