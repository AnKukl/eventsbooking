package com.example.eventsbooking.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * API answers may carry personal data, so neither the browser nor a proxy may keep them: {@code Cache-Control: no-store}
 * on everything under {@code /api}, errors included. The exception is the public catalogue, which sets its own caching
 * (the preview: public, 5 minutes; media: a year) or none.
 */
class NoStoreFilter extends OncePerRequestFilter {

    private static final Pattern PUBLIC_READ =
            Pattern.compile("/api/events(/[0-9]+(/preview)?)?/?|/api/media/[^/]+");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean read = "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
        if (path.startsWith("/api/") && !(read && PUBLIC_READ.matcher(path).matches())) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        }
        chain.doFilter(request, response);
    }
}
