package com.example.eventsbooking.storage;

import com.example.eventsbooking.support.TestImages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFileStorageTest {

    @TempDir
    Path directory;

    @Test
    void aSavedFileGetsAGeneratedNameWithTheRealExtensionAndCanBeReadBack() {
        LocalFileStorage storage = new LocalFileStorage(directory);

        String name = storage.save(TestImages.png(), ImageType.PNG);

        assertThat(name).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png");
        assertThat(storage.read(name)).hasValueSatisfying(bytes -> assertThat(bytes).isEqualTo(TestImages.png()));
        assertThat(directory.resolve(name)).exists();
    }

    @Test
    void everySaveGetsItsOwnName() {
        LocalFileStorage storage = new LocalFileStorage(directory);

        assertThat(storage.save(TestImages.png(), ImageType.PNG)).isNotEqualTo(storage.save(TestImages.png(), ImageType.PNG));
    }

    @Test
    void theDirectoryIsCreatedWhenMissing() {
        Path nested = directory.resolve("a/b/uploads");

        new LocalFileStorage(nested).save(TestImages.jpeg(), ImageType.JPEG);

        assertThat(nested).isDirectory();
    }

    @Test
    void namesThatCouldEscapeTheDirectoryAreNeverReadOrDeleted() throws IOException {
        Path outside = Files.writeString(directory.resolveSibling("secret-" + System.nanoTime() + ".txt"), "secret");
        Path inside = Files.createDirectory(directory.resolve("uploads"));
        LocalFileStorage storage = new LocalFileStorage(inside);

        for (String name : new String[] {"../" + outside.getFileName(), "..\\" + outside.getFileName(),
                outside.toAbsolutePath().toString(), "/etc/passwd", "a/../../x.png", ".png", "x.png/", "", "name with space.png",
                "0d9f2f5e-7c63-4c1f-9a60-0a3f7d1c7c11.exe", "0d9f2f5e-7c63-4c1f-9a60-0a3f7d1c7c11.png.exe"}) {
            assertThat(storage.read(name)).as("read %s", name).isEmpty();
            storage.delete(name);   // must neither fail nor touch anything
        }
        assertThat(outside).exists().hasContent("secret");
        Files.deleteIfExists(outside);
    }

    @Test
    void aMissingFileIsEmptyAndDeleteIsIdempotent() {
        LocalFileStorage storage = new LocalFileStorage(directory);
        String name = storage.save(TestImages.webp(), ImageType.WEBP);

        storage.delete(name);
        storage.delete(name);

        assertThat(storage.read(name)).isEmpty();
        assertThat(storage.read("0d9f2f5e-7c63-4c1f-9a60-0a3f7d1c7c11.png")).isEmpty();
    }
}
