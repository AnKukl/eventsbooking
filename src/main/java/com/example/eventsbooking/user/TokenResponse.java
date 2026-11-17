package com.example.eventsbooking.user;

import com.example.eventsbooking.security.IssuedToken;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

    static TokenResponse from(IssuedToken token) {
        return new TokenResponse(token.value(), "Bearer", token.expiresInSeconds());
    }

    @Override
    public String toString() {
        return "TokenResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
