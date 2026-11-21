package com.example.eventsbooking.user;

import com.example.eventsbooking.common.ConflictException;
import com.example.eventsbooking.common.TooManyRequestsException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
@Transactional
public class ProfileService {

    static final Duration TOKEN_TTL = Duration.ofHours(24);
    static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final EmailVerificationRepository verifications;
    private final EmailSender sender;
    private final Clock clock;

    public ProfileService(UserRepository users, EmailVerificationRepository verifications, EmailSender sender,
            Clock clock) {
        this.users = users;
        this.verifications = verifications;
        this.sender = sender;
        this.clock = clock;
    }

    /** {@code null} leaves a field as it is, an empty string clears it. */
    public User updateProfile(long userId, ProfileUpdateRequest request) {
        User user = user(userId);
        user.updateProfile(request.name(), request.phone(), request.language(), request.city());
        return user;
    }

    /**
     * Starts an email change: a one-time token valid for 24 hours is mailed to the NEW address; nothing changes until it
     * is confirmed. At most one request per minute per user. Only the hash of the token is stored.
     */
    public void requestEmailChange(long userId, String newEmail) {
        User user = user(userId);
        String email = newEmail.strip().toLowerCase(Locale.ROOT);
        if (email.equals(user.getEmail())) {
            if (user.isEmailVerified()) {
                throw new ConflictException("Email unchanged", "This is already your verified email address");
            }
        } else if (users.existsByEmail(email)) {
            throw new ConflictException("Email already in use", "This email address belongs to another account");
        }

        Instant now = clock.instant();
        verifications.findFirstByUserIdOrderByCreatedAtDesc(userId).ifPresent(last -> {
            Instant allowedAt = last.getCreatedAt().plus(RESEND_INTERVAL);
            if (now.isBefore(allowedAt)) {
                throw new TooManyRequestsException(Duration.between(now, allowedAt));
            }
        });

        String token = newToken();
        verifications.deleteByUserId(userId);   // the previous pending request, if any, stops working
        verifications.save(new EmailVerification(userId, email, hash(token), now, now.plus(TOKEN_TTL)));
        sender.sendVerification(userId, email, token);
    }

    /** One generic failure for unknown, used, replaced and expired tokens. */
    public void confirmEmail(String token) {
        EmailVerification verification = verifications.findByTokenHash(hash(token))
                .orElseThrow(InvalidVerificationTokenException::new);
        if (!clock.instant().isBefore(verification.getExpiresAt())) {
            throw new InvalidVerificationTokenException();
        }
        User user = user(verification.getUserId());
        if (!verification.getNewEmail().equals(user.getEmail()) && users.existsByEmail(verification.getNewEmail())) {
            throw new ConflictException("Email already in use", "This email address now belongs to another account");
        }
        user.changeEmail(verification.getNewEmail());
        verifications.delete(verification);
    }

    private User user(long userId) {
        return users.findById(userId).orElseThrow(() -> new BadCredentialsException("Unknown user"));
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }
}
