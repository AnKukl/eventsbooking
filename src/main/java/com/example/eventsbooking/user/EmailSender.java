package com.example.eventsbooking.user;

/** Delivers the confirmation message. The raw token must never be logged or stored by an implementation. */
public interface EmailSender {

    void sendVerification(long userId, String email, String token);
}
