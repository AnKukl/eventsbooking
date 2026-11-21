package com.example.eventsbooking.organizer;

import com.example.eventsbooking.event.Event;
import com.example.eventsbooking.event.EventMedia;
import com.example.eventsbooking.event.EventNotFoundException;
import com.example.eventsbooking.event.EventRepository;
import com.example.eventsbooking.event.MediaResponse;
import com.example.eventsbooking.storage.FileStorage;
import com.example.eventsbooking.storage.ImageType;
import com.example.eventsbooking.support.TestImages;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventMediaServiceTest {

    private static final int MAX_BYTES = 8 * 1024 * 1024;

    /** Keeps the files in memory so a test can see what was (not) stored. */
    static class InMemoryStorage implements FileStorage {
        final Map<String, byte[]> files = new LinkedHashMap<>();

        @Override
        public String save(byte[] content, ImageType type) {
            String name = UUID.randomUUID() + "." + type.extension();
            files.put(name, content);
            return name;
        }

        @Override
        public Optional<byte[]> read(String name) {
            return Optional.ofNullable(files.get(name));
        }

        @Override
        public void delete(String name) {
            files.remove(name);
        }
    }

    private final EventRepository events = mock(EventRepository.class);
    private final InMemoryStorage storage = new InMemoryStorage();
    private final EventMediaService service = new EventMediaService(events, storage);
    private final Event event = event(7L);

    // ---------- the real type decides ----------

    @Test
    void realImagesAreStoredAndAppendedInOrderWithTheirUrls() {
        List<MediaResponse> added = service.upload(5L, 7L, List.of(
                new UploadedImage("cover.png", TestImages.png()),
                new UploadedImage("second.jpeg", TestImages.jpeg()),
                new UploadedImage("third.webp", TestImages.webp())));

        assertThat(added).hasSize(3);
        assertThat(added).extracting(MediaResponse::url).allMatch(url -> url.startsWith("/api/media/"));
        assertThat(added.get(0).url()).endsWith(".png");
        assertThat(added.get(1).url()).endsWith(".jpg");
        assertThat(added.get(2).url()).endsWith(".webp");
        assertThat(event.getMedia()).extracting(EventMedia::url).containsExactlyElementsOf(
                added.stream().map(MediaResponse::url).toList());
        assertThat(storage.files).hasSize(3);
    }

    @Test
    void aTextFileNamedLikeAPictureIsRejectedAndNothingIsStored() {
        assertThatThrownBy(() -> service.upload(5L, 7L, List.of(new UploadedImage("photo.jpg", TestImages.text()))))
                .isInstanceOf(UnsupportedImageTypeException.class);

        assertThat(storage.files).isEmpty();
        assertThat(event.getMedia()).isEmpty();
    }

    @Test
    void anExecutableWithAPictureExtensionIsRejected() {
        for (String name : new String[] {"setup.png", "setup.jpg", "setup.webp"}) {
            assertThatThrownBy(() -> service.upload(5L, 7L, List.of(new UploadedImage(name, TestImages.exe()))))
                    .isInstanceOf(UnsupportedImageTypeException.class);
        }
        assertThat(storage.files).isEmpty();
    }

    @Test
    void anImageWhoseExtensionDisagreesWithItsContentIsRejectedToo() {
        assertThatThrownBy(() -> service.upload(5L, 7L, List.of(new UploadedImage("photo.jpg", TestImages.png()))))
                .isInstanceOf(UnsupportedImageTypeException.class).hasMessageContaining("does not match");
        assertThatThrownBy(() -> service.upload(5L, 7L, List.of(new UploadedImage("photo.png", TestImages.webp()))))
                .isInstanceOf(UnsupportedImageTypeException.class);
    }

    @Test
    void onlyJpgPngAndWebpAreAccepted() {
        for (UploadedImage file : List.of(new UploadedImage("anim.gif", TestImages.gif()),
                new UploadedImage("logo.svg", "<svg/>".getBytes()), new UploadedImage("noextension", TestImages.png()),
                new UploadedImage("photo.png.exe", TestImages.png()), new UploadedImage(null, TestImages.png()))) {
            assertThatThrownBy(() -> service.upload(5L, 7L, List.of(file)))
                    .isInstanceOf(UnsupportedImageTypeException.class);
        }
        assertThat(storage.files).isEmpty();
    }

    @Test
    void anEmptyFileIsRejected() {
        assertThatThrownBy(() -> service.upload(5L, 7L, List.of(new UploadedImage("a.png", new byte[0]))))
                .isInstanceOf(InvalidImageException.class);
    }

    // ---------- size ----------

    @Test
    void exactlyEightMegabytesIsAccepted() {
        service.upload(5L, 7L, List.of(new UploadedImage("big.png", TestImages.pngOfSize(MAX_BYTES))));

        assertThat(storage.files).hasSize(1);
    }

    @Test
    void oneByteOverEightMegabytesIsTooLarge() {
        assertThatThrownBy(() -> service.upload(5L, 7L,
                List.of(new UploadedImage("huge.png", TestImages.pngOfSize(MAX_BYTES + 1)))))
                .isInstanceOf(ImageTooLargeException.class);

        assertThat(storage.files).isEmpty();
    }

    // ---------- at most six images per event ----------

    @Test
    void anEventMayHaveSixImagesInTotal() {
        addExisting(5);

        service.upload(5L, 7L, List.of(new UploadedImage("sixth.png", TestImages.png())));

        assertThat(event.getMedia()).hasSize(6);
    }

    @Test
    void aSeventhImageIsRefusedAndTheWholeUploadIsDropped() {
        addExisting(5);

        assertThatThrownBy(() -> service.upload(5L, 7L, List.of(new UploadedImage("a.png", TestImages.png()),
                new UploadedImage("b.png", TestImages.png()))))
                .isInstanceOf(com.example.eventsbooking.common.ConflictException.class)
                .hasMessageContaining("6");

        assertThat(event.getMedia()).hasSize(5);
        assertThat(storage.files).isEmpty();
    }

    @Test
    void aBadFileAmongGoodOnesStoresNone() {
        assertThatThrownBy(() -> service.upload(5L, 7L, List.of(new UploadedImage("ok.png", TestImages.png()),
                new UploadedImage("bad.png", TestImages.text()))))
                .isInstanceOf(UnsupportedImageTypeException.class);

        assertThat(storage.files).isEmpty();
        assertThat(event.getMedia()).isEmpty();
    }

    // ---------- ownership ----------

    @Test
    void anotherOrganizersEventIsForbiddenAndNothingIsStored() {
        assertThatThrownBy(() -> service.upload(5L, 99L, List.of(new UploadedImage("a.png", TestImages.png()))))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(storage.files).isEmpty();
    }

    @Test
    void anUnknownEventIsNotFound() {
        when(events.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upload(404L, 7L, List.of(new UploadedImage("a.png", TestImages.png()))))
                .isInstanceOf(EventNotFoundException.class);
    }

    private void addExisting(int count) {
        List<EventMedia> existing = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            existing.add(new EventMedia("/api/media/existing-" + i + ".png", null));
        }
        event.applyPreview(null, null, null, existing);
    }

    private Event event(long organizerId) {
        Event created = new Event("Jazz", null, Instant.parse("2099-01-01T00:00:00Z"), 10, organizerId);
        ReflectionTestUtils.setField(created, "id", 5L);
        when(events.findById(5L)).thenReturn(Optional.of(created));
        return created;
    }
}
