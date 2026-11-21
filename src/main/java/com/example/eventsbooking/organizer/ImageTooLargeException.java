package com.example.eventsbooking.organizer;

/** A file over 8 MB; reported as 413. */
public class ImageTooLargeException extends RuntimeException {

    public ImageTooLargeException() {
        super("A photo may be at most 8 MB");
    }
}
