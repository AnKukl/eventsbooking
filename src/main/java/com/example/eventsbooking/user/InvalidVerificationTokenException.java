package com.example.eventsbooking.user;

/** Unknown, used, replaced or expired token: deliberately one exception, so the reason is not revealed. */
public class InvalidVerificationTokenException extends RuntimeException {

    public InvalidVerificationTokenException() {
        super("The confirmation link is invalid or has expired");
    }
}
