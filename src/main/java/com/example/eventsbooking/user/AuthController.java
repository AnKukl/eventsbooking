package com.example.eventsbooking.user;

import com.example.eventsbooking.security.AuthCookies;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService service;
    private final AuthCookies cookies;
    private final RefreshProperties refresh;

    AuthController(AuthService service, AuthCookies cookies, RefreshProperties refresh) {
        this.service = service;
        this.cookies = cookies;
        this.refresh = refresh;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(service.register(request));
    }

    @PostMapping("/login")
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return withCookies(service.startSession(request));
    }

    /** New access and refresh cookies for a valid refresh cookie; any failure clears both and answers 401. */
    @PostMapping("/refresh")
    ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = AuthCookies.REFRESH, required = false) String refreshToken, HttpServletResponse response) {
        try {
            return withCookies(service.refresh(refreshToken));
        } catch (RefreshTokenRejectedException e) {
            response.addHeader(HttpHeaders.SET_COOKIE, cookies.clearAccess().toString());
            response.addHeader(HttpHeaders.SET_COOKIE, cookies.clearRefresh().toString());
            throw e;
        }
    }

    /** Always succeeds: the refresh token is revoked if there is one, and the cookies are cleared. */
    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = AuthCookies.REFRESH, required = false) String refreshToken) {
        service.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clearAccess().toString())
                .header(HttpHeaders.SET_COOKIE, cookies.clearRefresh().toString())
                .build();
    }

    private ResponseEntity<LoginResponse> withCookies(AuthService.Session session) {
        long expiresIn = session.access().expiresInSeconds();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookies.access(session.access().value(), Duration.ofSeconds(expiresIn)).toString())
                .header(HttpHeaders.SET_COOKIE, cookies.refresh(session.refresh(), refresh.ttl()).toString())
                .body(new LoginResponse(expiresIn));
    }
}
