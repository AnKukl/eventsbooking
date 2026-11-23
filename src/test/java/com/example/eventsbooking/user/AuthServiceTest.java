package com.example.eventsbooking.user;

import com.example.eventsbooking.security.IssuedToken;
import com.example.eventsbooking.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private UserRepository users;
    private JwtService jwt;
    private AuthService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        jwt = mock(JwtService.class);
        service = new AuthService(users, encoder, jwt, Clock.fixed(NOW, ZoneOffset.UTC),
                new RegistrationProperties("invite-secret"), mock(RefreshTokenService.class));
        when(users.saveAndFlush(any(User.class))).thenAnswer(returnsFirstArg());
    }

    // ---------- registration ----------

    @Test
    void registrationStoresBCryptHashAndNeverThePlainPassword() {
        service.register(new RegisterRequest("alice@example.com", "s3cret-password", null));

        User saved = savedUser();
        assertThat(saved.getPasswordHash()).startsWith("$2").isNotEqualTo("s3cret-password");
        assertThat(encoder.matches("s3cret-password", saved.getPasswordHash())).isTrue();
    }

    @Test
    void registrationNormalisesEmail() {
        service.register(new RegisterRequest("  Alice@Example.COM ", "s3cret-password", null));

        assertThat(savedUser().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void registrationDefaultsToUserRole() {
        service.register(new RegisterRequest("alice@example.com", "s3cret-password", null));

        assertThat(savedUser().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void registrationWithTheCorrectInviteCodeCreatesAnOrganizer() {
        service.register(new RegisterRequest("org@example.com", "s3cret-password", "invite-secret"));

        assertThat(savedUser().getRole()).isEqualTo(Role.ORGANIZER);
    }

    @Test
    void registrationRecordsCreationTimeFromTheClock() {
        service.register(new RegisterRequest("alice@example.com", "s3cret-password", null));

        assertThat(savedUser().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void registrationRejectsAlreadyUsedEmail() {
        when(users.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                new RegisterRequest("Alice@example.com", "s3cret-password", null)))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void uniqueViolationFromARaceIsReportedAsAlreadyRegistered() {
        when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("users_email_key"));

        assertThatThrownBy(() -> service.register(
                new RegisterRequest("alice@example.com", "s3cret-password", null)))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void wrongInviteCodeIsDeniedAndNothingIsSaved() {
        assertThatThrownBy(() -> service.register(new RegisterRequest("org@example.com", "s3cret-password", "guess")))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void inviteCodeIsRejectedWhenNoCodeIsConfiguredSoOrganizersCannotRegisterAtAll() {
        for (String configured : new String[] {null, "", "  "}) {
            AuthService closed = new AuthService(users, encoder, jwt, Clock.fixed(NOW, ZoneOffset.UTC),
                    new RegistrationProperties(configured), mock(RefreshTokenService.class));

            assertThatThrownBy(() -> closed.register(
                    new RegisterRequest("org@example.com", "s3cret-password", "anything")))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        }
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void blankInviteCodeMeansAPlainUser() {
        service.register(new RegisterRequest("alice@example.com", "s3cret-password", "  "));

        assertThat(savedUser().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void wrongInviteCodeDoesNotRevealWhetherTheEmailIsTaken() {
        when(users.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("taken@example.com", "s3cret-password", "guess")))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void profileReturnsTheUserAndRejectsUnknownIds() {
        User user = new User("alice@example.com", "hash", Role.USER, NOW);
        when(users.findById(5L)).thenReturn(java.util.Optional.of(user));
        when(users.findById(6L)).thenReturn(java.util.Optional.empty());

        assertThat(service.profile(5L)).isSameAs(user);
        assertThatThrownBy(() -> service.profile(6L))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
    }

    // ---------- login ----------

    @Test
    void loginIssuesATokenForCorrectCredentials() {
        User user = new User("alice@example.com", encoder.encode("s3cret-password"), Role.USER, NOW);
        IssuedToken token = new IssuedToken("jwt-value", 3600);
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(jwt.issue(user)).thenReturn(token);

        assertThat(service.login(new LoginRequest(" Alice@Example.com ", "s3cret-password"))).isSameAs(token);
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = new User("alice@example.com", encoder.encode("s3cret-password"), Role.USER, NOW);
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new LoginRequest("alice@example.com", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);
        verify(jwt, never()).issue(any());
    }

    @Test
    void loginDoesNotRevealWhetherTheEmailExists() {
        User user = new User("alice@example.com", encoder.encode("s3cret-password"), Role.USER, NOW);
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        Throwable wrongPassword = catchThrowable(() -> service.login(new LoginRequest("alice@example.com", "nope-nope-nope")));
        Throwable unknownEmail = catchThrowable(() -> service.login(new LoginRequest("nobody@example.com", "nope-nope-nope")));

        assertThat(unknownEmail).isInstanceOf(BadCredentialsException.class);
        assertThat(unknownEmail.getMessage()).isEqualTo(wrongPassword.getMessage());
    }

    // ---------- helpers ----------

    private User savedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private static Throwable catchThrowable(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        return org.assertj.core.api.Assertions.catchThrowable(callable);
    }
}
