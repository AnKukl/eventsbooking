package com.example.eventsbooking.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * CSRF protection for the cookie session. A browser attaches the session cookies to any request, also one started by
 * another site, but it always tells where the request comes from in {@code Origin}. So a state-changing request that
 * carries a session cookie must come from an allowed frontend origin or from the API's own origin (Swagger UI);
 * otherwise (also without {@code Origin}) it is refused with 403. A request without session cookies is not checked:
 * its credentials (a Bearer header) are never added by a browser on its own.
 */
class OriginCheckFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final Set<String> allowedOrigins;
    private final AccessDeniedHandler denied;

    OriginCheckFilter(Set<String> allowedOrigins, AccessDeniedHandler denied) {
        this.allowedOrigins = allowedOrigins;
        this.denied = denied;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod()) || !hasSessionCookie(request)
                || isAllowed(request.getHeader(HttpHeaders.ORIGIN), request)) {
            chain.doFilter(request, response);
            return;
        }
        denied.handle(request, response, new AccessDeniedException("Cross-site request rejected"));
    }

    private boolean isAllowed(String origin, HttpServletRequest request) {
        if (origin == null || origin.isBlank() || "null".equals(origin)) {
            return false;
        }
        return allowedOrigins.contains(origin) || origin.equals(ownOrigin(request));
    }

    private static String ownOrigin(HttpServletRequest request) {
        String scheme = request.getScheme();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port);
    }

    private static boolean hasSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (AuthCookies.ACCESS.equals(cookie.getName()) || AuthCookies.REFRESH.equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }
}
