package com.example.eventsbooking.security;

import com.example.eventsbooking.user.Role;
import com.example.eventsbooking.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit tests: time comes from fixed clocks, nothing sleeps and nothing reads the real "now". */
class JwtServiceTest {

    private static final Instant ISSUED_AT = Instant.parse("2030-01-01T10:00:00Z");
    private static final String SECRET = "unit-test-secret-unit-test-secret-0123456789";
    private static final String OTHER_SECRET = "another-secret-another-secret-0123456789!";
    private static final String ISSUER = "eventsbooking";
    private static final Duration TTL = Duration.ofHours(1);

    @Test
    void issuedTokenCarriesSubjectRoleIssuerAndExpiry() {
        String token = issue(SECRET, Role.ORGANIZER).value();

        Jwt jwt = decoderAt(ISSUED_AT, SECRET).decode(token);

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ORGANIZER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(ISSUER);
        assertThat(jwt.getIssuedAt()).isEqualTo(ISSUED_AT);
        assertThat(jwt.getExpiresAt()).isEqualTo(ISSUED_AT.plus(TTL));
    }

    @Test
    void issuedTokenReportsItsLifetimeInSeconds() {
        assertThat(issue(SECRET, Role.USER).expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    void tokenIsAcceptedOneSecondBeforeExpiry() {
        String token = issue(SECRET, Role.USER).value();

        assertThat(decoderAt(ISSUED_AT.plus(TTL).minusSeconds(1), SECRET).decode(token)).isNotNull();
    }

    @Test
    void tokenIsRejectedOneSecondAfterExpiry() {
        String token = issue(SECRET, Role.USER).value();

        assertThatThrownBy(() -> decoderAt(ISSUED_AT.plus(TTL).plusSeconds(1), SECRET).decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String forged = issue(OTHER_SECRET, Role.ORGANIZER).value();

        assertThatThrownBy(() -> decoderAt(ISSUED_AT, SECRET).decode(forged))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenWithTamperedRoleIsRejected() {
        String token = issue(SECRET, Role.USER).value();
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String escalated = payload.replace("\"USER\"", "\"ORGANIZER\"");
        assertThat(escalated).isNotEqualTo(payload);
        String tampered = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(escalated.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        assertThatThrownBy(() -> decoderAt(ISSUED_AT, SECRET).decode(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        String token = issue(SECRET, Role.USER).value();
        JwtDecoder strict = JwtConfig.decoder(JwtConfig.secretKey(SECRET), "someone-else",
                Clock.fixed(ISSUED_AT, ZoneOffset.UTC));

        assertThatThrownBy(() -> strict.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void secretShorterThan32BytesIsRejectedAndNeverEchoed() {
        assertThatThrownBy(() -> JwtConfig.secretKey("short-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET")
                .hasMessageNotContaining("short-secret");
    }

    @Test
    void missingSecretIsRejected() {
        assertThatThrownBy(() -> JwtConfig.secretKey(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> JwtConfig.secretKey("   ")).isInstanceOf(IllegalStateException.class);
    }

    // ---------- helpers ----------

    private static IssuedToken issue(String secret, Role role) {
        JwtService service = new JwtService(JwtConfig.encoder(JwtConfig.secretKey(secret)),
                new JwtProperties(secret, ISSUER, TTL), Clock.fixed(ISSUED_AT, ZoneOffset.UTC));
        User user = new User("user@example.com", "hash", role, ISSUED_AT);
        ReflectionTestUtils.setField(user, "id", 42L);
        return service.issue(user);
    }

    private static JwtDecoder decoderAt(Instant now, String secret) {
        return JwtConfig.decoder(JwtConfig.secretKey(secret), ISSUER, Clock.fixed(now, ZoneOffset.UTC));
    }

}
