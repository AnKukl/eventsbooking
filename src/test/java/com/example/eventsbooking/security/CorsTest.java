package com.example.eventsbooking.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A browser frontend on another origin must be able to call the API; other origins must not. No database needed. */
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CorsTest {

    private static final String FRONTEND = "http://localhost:5173";

    private final MockMvc mockMvc;

    CorsTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void preflightFromTheFrontendOriginIsAllowedWithoutAToken() throws Exception {
        mockMvc.perform(options("/api/events")
                        .header(HttpHeaders.ORIGIN, FRONTEND)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        org.hamcrest.Matchers.containsString("POST")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        org.hamcrest.Matchers.containsStringIgnoringCase("authorization")));
    }

    @Test
    void preflightFromAForeignOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/events")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void errorResponsesCarryCorsHeadersSoTheFrontendCanReadThem() throws Exception {
        // a protected endpoint: the open catalog (GET /api/events) would not produce a 401 for a guest
        mockMvc.perform(get("/api/me").header(HttpHeaders.ORIGIN, FRONTEND))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND));
    }

    @Test
    void aPatchPreflightWithTheHeadersTheFrontendSendsIsAllowed() throws Exception {
        mockMvc.perform(options("/api/me")
                        .header(HttpHeaders.ORIGIN, FRONTEND)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "content-type,idempotency-key,if-match,if-none-match"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        org.hamcrest.Matchers.containsString("PATCH")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsStringIgnoringCase("idempotency-key"),
                        org.hamcrest.Matchers.containsStringIgnoringCase("if-match"),
                        org.hamcrest.Matchers.containsStringIgnoringCase("if-none-match"))));
    }

    @Test
    void aPatchPreflightFromAForeignOriginGetsNoAllowOrigin() throws Exception {
        mockMvc.perform(options("/api/me")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void credentialsAreAllowedForTheListedOriginsBecauseTheSessionIsACookie() throws Exception {
        mockMvc.perform(options("/api/events")
                        .header(HttpHeaders.ORIGIN, FRONTEND)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void etagLocationAndRetryAfterAreReadableByTheFrontend() throws Exception {
        mockMvc.perform(get("/api/me").header(HttpHeaders.ORIGIN, FRONTEND))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("ETag"),
                        org.hamcrest.Matchers.containsString("Location"),
                        org.hamcrest.Matchers.containsString("Retry-After"))));
    }

    @Test
    void aWildcardOriginNeverComesWithCredentials() {
        var source = (org.springframework.web.cors.UrlBasedCorsConfigurationSource)
                new SecurityConfig().corsConfigurationSource(java.util.List.of("*"));
        var config = source.getCorsConfigurations().get("/**");

        org.assertj.core.api.Assertions.assertThat(config.getAllowCredentials()).isFalse();
    }
}
