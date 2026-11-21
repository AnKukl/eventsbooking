package com.example.eventsbooking.storage;

import com.example.eventsbooking.support.MutableClock;
import com.example.eventsbooking.support.TestImages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class EventImageServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @TempDir
    Path directory;

    private final EventImageRepository images = mock(EventImageRepository.class);
    private final MutableClock clock = new MutableClock(NOW);

    private List<String> filesOnDisk() throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(file -> file.getFileName().toString()).toList();
        }
    }

    @Test
    void afterAnUploadTheFileIsInPlaceAndTheRowIsPending() throws IOException {
        EventImageService service = new EventImageService(new LocalFileStorage(directory), images, clock);

        String key = service.store(TestImages.png(), ImageType.PNG);

        assertThat(key).endsWith(".png");
        assertThat(filesOnDisk()).containsExactly(key);            // only the final name: no temporary file is left
        assertThat(Files.readAllBytes(directory.resolve(key))).isEqualTo(TestImages.png());
        verify(images).insertPending(key, NOW);
    }

    @Test
    void ifTheRowCannotBeWrittenTheFileIsRemoved() throws IOException {
        doThrow(new IllegalStateException("database is down")).when(images).insertPending(anyString(), any());
        EventImageService service = new EventImageService(new LocalFileStorage(directory), images, clock);

        assertThatThrownBy(() -> service.store(TestImages.png(), ImageType.PNG))
                .isInstanceOf(IllegalStateException.class);

        assertThat(filesOnDisk()).isEmpty();
    }

    @Test
    void ifTheRenameFailsAfterTheFileWasWrittenTheFileIsRemoved() throws IOException {
        LocalFileStorage storage = spy(new LocalFileStorage(directory));
        doThrow(new UncheckedIOException("disk error", new IOException("x"))).when(storage).move(anyString(), anyString());
        EventImageService service = new EventImageService(storage, images, clock);

        assertThatThrownBy(() -> service.store(TestImages.png(), ImageType.PNG))
                .isInstanceOf(UncheckedIOException.class);

        assertThat(filesOnDisk()).isEmpty();
    }

    @Test
    void attachingMarksTheRowsOfTheEvent() {
        EventImageService service = new EventImageService(new LocalFileStorage(directory), images, clock);

        service.attach(5L, List.of("a.png", "b.png"));

        verify(images).attach(5L, List.of("a.png", "b.png"));
    }
}
