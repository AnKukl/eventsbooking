package com.example.eventsbooking.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param organizerInviteCode secret that lets a registration become ORGANIZER. Empty (the default) means nobody
 *                            can register as an organizer. Comes from the ORGANIZER_INVITE_CODE environment variable.
 */
@ConfigurationProperties("app.registration")
public record RegistrationProperties(String organizerInviteCode) {

    /** The invite code is a secret: never print it. */
    @Override
    public String toString() {
        return "RegistrationProperties[organizerInviteCode=***]";
    }
}
