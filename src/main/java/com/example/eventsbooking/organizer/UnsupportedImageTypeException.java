package com.example.eventsbooking.organizer;

/** Not a JPEG, PNG or WebP picture, or the file name claims a different type than the content has; reported as 415. */
public class UnsupportedImageTypeException extends RuntimeException {

    public UnsupportedImageTypeException(String message) {
        super(message);
    }
}
