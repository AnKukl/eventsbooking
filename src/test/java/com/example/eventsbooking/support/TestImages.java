package com.example.eventsbooking.support;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Smallest byte sequences that start like real image files (the checks look at the signature, not at the picture). */
public final class TestImages {

    private TestImages() {
    }

    public static byte[] png() {
        return concat(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, filler(24));
    }

    public static byte[] jpeg() {
        return concat(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0x00},
                filler(24));
    }

    public static byte[] webp() {
        return concat("RIFF".getBytes(StandardCharsets.US_ASCII), new byte[] {0x1A, 0, 0, 0},
                "WEBPVP8 ".getBytes(StandardCharsets.US_ASCII), filler(16));
    }

    /** A RIFF container that is not WebP: it must not be mistaken for an image. */
    public static byte[] wav() {
        return concat("RIFF".getBytes(StandardCharsets.US_ASCII), new byte[] {0x1A, 0, 0, 0},
                "WAVEfmt ".getBytes(StandardCharsets.US_ASCII), filler(16));
    }

    public static byte[] gif() {
        return concat("GIF89a".getBytes(StandardCharsets.US_ASCII), filler(16));
    }

    public static byte[] text() {
        return "This is not a picture, only text named like one.".getBytes(StandardCharsets.UTF_8);
    }

    /** A Windows executable header. */
    public static byte[] exe() {
        return concat(new byte[] {'M', 'Z', (byte) 0x90, 0x00}, filler(32));
    }

    /** {@code type} with the right signature padded to exactly {@code size} bytes. */
    public static byte[] pngOfSize(int size) {
        byte[] bytes = new byte[size];
        byte[] head = png();
        System.arraycopy(head, 0, bytes, 0, Math.min(head.length, size));
        return bytes;
    }

    private static byte[] filler(int length) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) 0x42);
        return bytes;
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] all = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, all, offset, part.length);
            offset += part.length;
        }
        return all;
    }
}
