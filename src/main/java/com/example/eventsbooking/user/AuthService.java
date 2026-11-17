package com.example.eventsbooking.user;

import com.example.eventsbooking.security.IssuedToken;
import com.example.eventsbooking.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;
    /** Checked against when the email is unknown, so that response time does not reveal which emails exist. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("timing-equalisation-only");
    }

    @Transactional
    public User register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        Role role = request.role() == null ? Role.USER : request.role();
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

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
