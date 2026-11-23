package com.example.eventsbooking.user;

import com.example.eventsbooking.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Login, registration and refresh are limited to 10 attempts a minute; the 11th gets 429 with Retry-After. */
@WebMvcTest(controllers = AuthController.class, properties = "app.security.rate-limit.attempts=10")
@Import({SecurityConfig.class, AuthRateLimiter.class, AuthRateLimitControllerTest.FixedClock.class})
@EnableConfigurationProperties({RefreshProperties.class, RateLimitProperties.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuthRateLimitControllerTest {

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2030-01-01T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    private final MockMvc mockMvc;

    @MockitoBean
    private AuthService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    AuthRateLimitControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void theEleventhLoginInAMinuteGets429WithRetryAfter() throws Exception {
        when(service.startSession(any(LoginRequest.class))).thenThrow(new BadCredentialsException("x"));
        for (int i = 0; i < 10; i++) {
            login().andExpect(status().isUnauthorized());
        }

        login().andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void theEleventhRefreshInAMinuteGets429() throws Exception {
        for (int i = 0; i < 10; i++) {
            refresh().andExpect(status().isOk());
        }

        refresh().andExpect(status().isTooManyRequests()).andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    private org.springframework.test.web.servlet.ResultActions login() throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(request -> {
                    request.setRemoteAddr("192.0.2.10");
                    return request;
                }).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"alice@example.com\",\"password\":\"s3cret-password\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions refresh() throws Exception {
        when(service.refresh(any())).thenReturn(new AuthService.Session(
                new com.example.eventsbooking.security.IssuedToken("a", 900), "r"));
        return mockMvc.perform(post("/api/auth/refresh").with(request -> {
            request.setRemoteAddr("192.0.2.20");
            return request;
        }));
    }
}
