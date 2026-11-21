package com.example.eventsbooking.storage;

import java.util.Locale;
import java.util.Optional;

/**
 * The picture formats accepted for event photos. The real type is read from the first bytes of the content (the file
 * signature); the file name only tells what the sender claims, and the two must agree.
 */
public enum ImageType {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private final String contentType;
    private final String extension;

    ImageType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    /** The extension used for stored files, whatever the uploaded file was called. */
    public String extension() {
        return extension;
    }

    /** The type of the content itself: JPEG, PNG or WebP by signature; anything else (text, GIF, SVG, exe...) is empty. */
    public static Optional<ImageType> detect(byte[] content) {
        if (content == null) {
            return Optional.empty();
        }
        if (startsWith(content, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        // WebP is a RIFF container: "RIFF", four size bytes, then "WEBP" (a WAV or AVI also begins with RIFF)
        if (startsWith(content, 0, 'R', 'I', 'F', 'F') && startsWith(content, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    /** What the file name claims: only the final extension counts, case-insensitively; unknown ones are empty. */
    public static Optional<ImageType> fromFileName(String fileName) {
        if (fileName == null) {
            return Optional.empty();
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return Optional.empty();
        }
        return switch (fileName.substring(dot + 1).toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> Optional.of(JPEG);
            case "png" -> Optional.of(PNG);
            case "webp" -> Optional.of(WEBP);
            default -> Optional.empty();
        };
    }

    /** The type of a file this application stored (its name ends with the extension chosen at upload). */
    public static Optional<ImageType> fromStoredName(String storedName) {
        return fromFileName(storedName);
    }

    private static boolean startsWith(byte[] content, int offset, int... signature) {
        if (content.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((content[offset + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
