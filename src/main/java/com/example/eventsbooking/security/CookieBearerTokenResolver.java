package com.example.eventsbooking.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * The access token comes from the {@code Authorization} header (tests, Swagger, scripts) or, if there is none, from the
 * {@code access_token} cookie (the browser). On {@code /api/auth/**} the cookie is ignored: an expired cookie must not
 * stop anybody from logging in, refreshing or logging out.
 */
class CookieBearerTokenResolver implements BearerTokenResolver {

    private final DefaultBearerTokenResolver header = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        String fromHeader = header.resolve(request);
        if (fromHeader != null) {
            return fromHeader;
        }
        if (request.getRequestURI().startsWith(request.getContextPath() + "/api/auth/")) {
            return null;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AuthCookies.ACCESS.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
