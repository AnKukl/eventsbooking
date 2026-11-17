package com.example.eventsbooking.user;

import com.example.eventsbooking.security.IssuedToken;
import com.example.eventsbooking.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.access.AccessDeniedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;
    private final RegistrationProperties registration;
    /** Checked against when the email is unknown, so that response time does not reveal which emails exist. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService, Clock clock,
            RegistrationProperties registration) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
        this.registration = registration;
        this.dummyHash = passwordEncoder.encode("timing-equalisation-only");
    }

    @Transactional
    public User register(RegisterRequest request) {
        // checked first, so a wrong invite code cannot be used to probe which emails exist
        Role role = roleFor(request.inviteCode());
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        try {
            return users.saveAndFlush(
                    new User(email, passwordEncoder.encode(request.password()), role, clock.instant()));
        } catch (DataIntegrityViolationException e) {
            // two registrations of the same email raced past the existsByEmail check; the DB constraint won
            throw new EmailAlreadyRegisteredException();
        }
    }

    public IssuedToken login(LoginRequest request) {
        User user = users.findByEmail(normalize(request.email())).orElse(null);
        String hash = user == null ? dummyHash : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !passwordMatches) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return jwtService.issue(user);
    }

    /** The authenticated user's account; a token for a deleted user is no longer valid. */
    public User profile(long userId) {
        return users.findById(userId).orElseThrow(() -> new BadCredentialsException("Unknown user"));
    }

    private Role roleFor(String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            return Role.USER;
        }
        String expected = registration.organizerInviteCode();
        boolean valid = expected != null && !expected.isBlank()
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                        inviteCode.getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            throw new AccessDeniedException("Invalid organizer invite code");
        }
        return Role.ORGANIZER;
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
