package com.example.eventsbooking.user;

import org.springframework.security.core.AuthenticationException;

/** The refresh token is missing, unknown, expired or revoked: the client has to log in again (401). */
public class RefreshTokenRejectedException extends AuthenticationException {

    public RefreshTokenRejectedException(String reason) {
        super(reason);
    }
}
