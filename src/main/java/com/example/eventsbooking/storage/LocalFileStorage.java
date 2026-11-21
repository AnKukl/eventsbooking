package com.example.eventsbooking.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Files in one directory under names the storage generated. Because a name is accepted only if it has exactly the
 * generated shape (UUID + known extension), a name can never contain a path separator or "..": reading or deleting
 * outside the directory is not possible, whatever a client sends.
 */
public class LocalFileStorage implements FileStorage {

    /** The shape of a name this storage generates; the controller uses the same check before asking for a file. */
    public static final Pattern STORED_NAME =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)");

    private final Path root;

    public LocalFileStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public String save(byte[] content, ImageType type) {
        String name = UUID.randomUUID() + "." + type.extension();
        try {
            Files.createDirectories(root);
            Files.write(root.resolve(name), content);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store the uploaded file", e);
        }
        return name;
    }

    @Override
    public Optional<byte[]> read(String name) {
        if (!isStoredName(name)) {
            return Optional.empty();
        }
        Path file = root.resolve(name);
        try {
            return Files.isRegularFile(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the stored file", e);
        }
    }

    @Override
    public void delete(String name) {
        if (!isStoredName(name)) {
            return;
        }
        try {
            Files.deleteIfExists(root.resolve(name));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete the stored file", e);
        }
    }

    public static boolean isStoredName(String name) {
        return name != null && STORED_NAME.matcher(name).matches();
    }
}
