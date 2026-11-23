package com.example.eventsbooking.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Issues, rotates and revokes refresh tokens. A token is 256 random bits; the database keeps only its SHA-256 hash.
 *
 * <p>Rotation locks the presented row ({@code FOR UPDATE}), so of two concurrent refreshes with one token exactly one
 * wins; the other then finds the token revoked. Whether a revoked token means a race or theft is decided by how long
 * ago it was rotated (see {@link RefreshProperties#reuseGrace()}).
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** A new refresh token for the cookie and the user it belongs to. The value is a credential: never log it. */
    public record Issued(long userId, String value) {
        @Override
        public String toString() {
            return "Issued[userId=" + userId + "]";
        }
    }

    private record Row(long id, long userId, UUID familyId, Instant expiresAt, Instant revokedAt, Long replacedBy) {
    }

    private final JdbcClient jdbc;
    private final Clock clock;
    private final RefreshProperties properties;

    public RefreshTokenService(JdbcClient jdbc, Clock clock, RefreshProperties properties) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.properties = properties;
    }

    /** The first token of a new family (a login). */
    @Transactional
    public Issued issue(long userId) {
        String value = newValue();
        insert(userId, value, UUID.randomUUID(), clock.instant());
        return new Issued(userId, value);
    }

    /**
     * Exchanges a valid token for a new one of the same family. A revoked token presented after the grace period
     * revokes the whole family; the revocation is committed although the call ends with an exception.
     */
    @Transactional(noRollbackFor = RefreshTokenRejectedException.class)
    public Issued rotate(String value) {
        if (value == null || value.isBlank()) {
            throw new RefreshTokenRejectedException("No refresh token");
        }
        Instant now = clock.instant();
        Row row = jdbc.sql("""
                SELECT id, user_id, family_id, expires_at, revoked_at, replaced_by FROM refresh_tokens
                WHERE token_hash = :hash FOR UPDATE
                """)
                .param("hash", hash(value))
                .query((rs, n) -> new Row(rs.getLong("id"), rs.getLong("user_id"), rs.getObject("family_id", UUID.class),
                        rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("revoked_at", OffsetDateTime.class) == null ? null
                                : rs.getObject("revoked_at", OffsetDateTime.class).toInstant(),
                        (Long) rs.getObject("replaced_by")))
                .optional()
                .orElseThrow(() -> new RefreshTokenRejectedException("Unknown refresh token"));

        if (row.revokedAt() != null) {
            boolean race = row.replacedBy() != null && row.revokedAt().plus(properties.reuseGrace()).isAfter(now);
            if (!race) {
                int revoked = revokeFamily(row.familyId(), now);
                log.warn("Revoked refresh token reused: family of user {} revoked ({} live tokens)", row.userId(), revoked);
            }
            throw new RefreshTokenRejectedException("Refresh token was already used");
        }
        if (!row.expiresAt().isAfter(now)) {
            throw new RefreshTokenRejectedException("Refresh token expired");
        }

        String next = newValue();
        long nextId = insert(row.userId(), next, row.familyId(), now);
        jdbc.sql("UPDATE refresh_tokens SET revoked_at = :now, replaced_by = :next WHERE id = :id")
                .param("now", utc(now)).param("next", nextId).param("id", row.id())
                .update();
        return new Issued(row.userId(), next);
    }

    /** Logout: the token stops working. A missing or unknown token is fine. */
    @Transactional
    public void revoke(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        jdbc.sql("UPDATE refresh_tokens SET revoked_at = :now WHERE token_hash = :hash AND revoked_at IS NULL")
                .param("now", utc(clock.instant())).param("hash", hash(value))
                .update();
    }

    private int revokeFamily(UUID familyId, Instant now) {
        return jdbc.sql("UPDATE refresh_tokens SET revoked_at = :now WHERE family_id = :family AND revoked_at IS NULL")
                .param("now", utc(now)).param("family", familyId)
                .update();
    }

    private long insert(long userId, String value, UUID familyId, Instant now) {
        return jdbc.sql("""
                INSERT INTO refresh_tokens (user_id, token_hash, family_id, created_at, expires_at)
                VALUES (:userId, :hash, :family, :now, :expires) RETURNING id
                """)
                .param("userId", userId).param("hash", hash(value)).param("family", familyId)
                .param("now", utc(now)).param("expires", utc(now.plus(properties.ttl())))
                .query(Long.class).single();
    }

    private static String newValue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
