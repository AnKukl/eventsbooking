package com.example.eventsbooking.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Stateless API protected by JWT bearer tokens.
 *
 * <p>CSRF protection is disabled on purpose: there are no sessions and no cookies, the token is sent
 * explicitly in the {@code Authorization} header, so a browser cannot be tricked into attaching it.
 *
 * <p>Deny by default: everything not listed below requires an authenticated user.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@org.springframework.boot.context.properties.EnableConfigurationProperties(CookieProperties.class)
public class SecurityConfig {

    static final String ROLE_ORGANIZER = "ORGANIZER";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailSecurityHandlers problemHandlers,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            @Value("${app.cors.allowed-origins:}") List<String> allowedOrigins) throws Exception {
        http
                // our own Cache-Control policy (no-store for private data) replaces the blanket default of Spring Security
                .headers(headers -> headers.cacheControl(cache -> cache.disable()))
                .addFilterBefore(new NoStoreFilter(), org.springframework.web.filter.CorsFilter.class)
                // CSRF for the cookie session: state-changing requests with a session cookie must come from an allowed origin
                .addFilterBefore(new OriginCheckFilter(java.util.Set.copyOf(clean(allowedOrigins)), problemHandlers),
                        org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class)
                // CORS is evaluated before authentication, so preflight requests need no token and 401/403 carry CORS headers
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/confirm-email",
                                "/api/auth/logout", "/api/auth/refresh").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // API description for developers: public, contains no data
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        // the catalog is public for guests (isFavorite is false for them); changing it is not
                        .requestMatchers(HttpMethod.GET, "/api/events", "/api/events/*", "/api/events/*/preview").permitAll()
                        // uploaded pictures are shown on public pages; the cabinet is for organizers only
                        .requestMatchers(HttpMethod.GET, "/api/media/*").permitAll()
                        // the booking rules (seat limit, hold time) are shown before signing in
                        .requestMatchers(HttpMethod.GET, "/api/settings/booking").permitAll()
                        .requestMatchers("/api/organizer/**").hasRole(ROLE_ORGANIZER)
                        .requestMatchers(HttpMethod.POST, "/api/events").hasRole(ROLE_ORGANIZER)
                        .requestMatchers(HttpMethod.PUT, "/api/events/*").hasRole(ROLE_ORGANIZER)
                        .requestMatchers(HttpMethod.DELETE, "/api/events/*").hasRole(ROLE_ORGANIZER)
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(new CookieBearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers));
        return http.build();
    }

    /**
     * Browser frontends on another origin. Only the listed origins are allowed. Credentials (the session cookies) are
     * allowed only for an explicit list: with "*" any site could make the browser send them, so then they are off.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:}") List<String> allowedOrigins) {
        List<String> origins = clean(allowedOrigins);
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Idempotency-Key", "If-Match",
                "If-None-Match"));
        cors.setExposedHeaders(List.of("ETag", "Location", "Retry-After"));
        cors.setAllowCredentials(!origins.isEmpty() && !origins.contains(CorsConfiguration.ALL));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    private static List<String> clean(List<String> origins) {
        return origins.stream().map(String::strip).filter(origin -> !origin.isEmpty()).toList();
    }

    @Bean
    AuthCookies authCookies(CookieProperties properties) {
        return new AuthCookies(properties);
    }

    /** Maps the {@code roles} claim (["ORGANIZER"]) to authorities ({@code ROLE_ORGANIZER}). */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    ProblemDetailSecurityHandlers problemDetailSecurityHandlers(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        return new ProblemDetailSecurityHandlers(resolver);
    }

    /** BCrypt with a per-password random salt; the stored hash starts with {@code $2a$}. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
