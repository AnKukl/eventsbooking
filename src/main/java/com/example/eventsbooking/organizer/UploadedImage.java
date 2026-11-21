package com.example.eventsbooking.organizer;

/** An uploaded file as the service sees it: the claimed name (only used for the extension check) and the bytes. */
public record UploadedImage(String filename, byte[] content) {
}
