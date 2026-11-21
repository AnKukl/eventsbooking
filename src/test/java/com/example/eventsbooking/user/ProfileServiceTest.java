package com.example.eventsbooking.user;

import com.example.eventsbooking.common.ConflictException;
import com.example.eventsbooking.common.TooManyRequestsException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    private final UserRepository users = mock(UserRepository.class);
    private final EmailVerificationRepository verifications = mock(EmailVerificationRepository.class);
    private final EmailSender sender = mock(EmailSender.class);
    private final ProfileService service =
            new ProfileService(users, verifications, sender, Clock.fixed(NOW, ZoneOffset.UTC));

    private final User user = existingUser();

    // ---------- profile ----------

    @Test
    void updateChangesOnlyTheGivenFields() {
        user.updateProfile("Anna", null, null, "Gdansk");

        service.updateProfile(7L, new ProfileUpdateRequest(null, "+48123456789", "PL", null));

        assertThat(user.getName()).isEqualTo("Anna");
        assertThat(user.getPhone()).isEqualTo("+48123456789");
        assertThat(user.getLanguage()).isEqualTo("PL");
        assertThat(user.getCity()).isEqualTo("Gdansk");
    }

    @Test
    void anEmptyStringClearsAValue() {
        user.updateProfile("Anna", "+48123456789", "PL", "Gdansk");

        service.updateProfile(7L, new ProfileUpdateRequest("", "", null, ""));

        assertThat(user.getName()).isNull();
        assertThat(user.getPhone()).isNull();
        assertThat(user.getCity()).isNull();
        assertThat(user.getLanguage()).isEqualTo("PL");
    }

    @Test
    void updatingTheProfileOfADeletedUserIsUnauthorized() {
        when(users.findById(8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProfile(8L, new ProfileUpdateRequest("A", null, null, null)))
                .isInstanceOf(BadCredentialsException.class);
    }

    // ---------- requesting an email change ----------

    @Test
    void theTokenIsStoredOnlyAsAHashWithA24HourLifetimeAndTheRawTokenGoesToTheMail() {
        service.requestEmailChange(7L, "  New@Example.COM ");

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(sender).sendVerification(eq7(), eq("new@example.com"), token.capture());
        ArgumentCaptor<EmailVerification> saved = ArgumentCaptor.forClass(EmailVerification.class);
        verify(verifications).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(ProfileService.hash(token.getValue()))
                .isNotEqualTo(token.getValue()).hasSize(64);
        assertThat(token.getValue()).hasSizeGreaterThanOrEqualTo(43);
        assertThat(saved.getValue().getNewEmail()).isEqualTo("new@example.com");
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(user.getEmail()).as("nothing changes before the confirmation").isEqualTo("old@example.com");
    }

    @Test
    void aNewRequestReplacesThePreviousPendingOne() {
        service.requestEmailChange(7L, "new@example.com");

        verify(verifications).deleteByUserId(7L);
    }

    @Test
    void everyRequestGetsAnotherToken() {
        service.requestEmailChange(7L, "new@example.com");
        service.requestEmailChange(7L, "new@example.com");

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(sender, org.mockito.Mockito.times(2)).sendVerification(anyLong(), anyString(), token.capture());
        assertThat(token.getAllValues().get(0)).isNotEqualTo(token.getAllValues().get(1));
    }

    @Test
    void resendingWithinAMinuteIsRefusedWithTheRemainingWait() {
        when(verifications.findFirstByUserIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.of(
                new EmailVerification(7L, "new@example.com", "h", NOW.minusSeconds(40), NOW.plusSeconds(86_000))));

        assertThatThrownBy(() -> service.requestEmailChange(7L, "new@example.com"))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofSeconds(20)));

        verify(verifications, never()).save(any());
        verify(sender, never()).sendVerification(anyLong(), anyString(), anyString());
    }

    @Test
    void resendingExactlyAMinuteLaterIsAllowed() {
        when(verifications.findFirstByUserIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.of(
                new EmailVerification(7L, "new@example.com", "h", NOW.minusSeconds(60), NOW.plusSeconds(86_000))));

        assertThatCode(() -> service.requestEmailChange(7L, "new@example.com")).doesNotThrowAnyException();
    }

    @Test
    void oneSecondBeforeAMinuteIsStillTooEarly() {
        when(verifications.findFirstByUserIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.of(
                new EmailVerification(7L, "new@example.com", "h", NOW.minusSeconds(59), NOW.plusSeconds(86_000))));

        assertThatThrownBy(() -> service.requestEmailChange(7L, "new@example.com"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void anEmailThatBelongsToSomebodyElseIsAConflictAndSendsNothing() {
        when(users.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.requestEmailChange(7L, "Taken@example.com"))
                .isInstanceOf(ConflictException.class);

        verify(sender, never()).sendVerification(anyLong(), anyString(), anyString());
    }

    @Test
    void theAlreadyVerifiedCurrentEmailCannotBeRequestedAgain() {
        ReflectionTestUtils.setField(user, "emailVerified", true);

        assertThatThrownBy(() -> service.requestEmailChange(7L, "old@example.com"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void theCurrentButUnverifiedEmailCanBeVerified() {
        service.requestEmailChange(7L, "old@example.com");

        verify(sender).sendVerification(eq7(), eq("old@example.com"), anyString());
    }

    // ---------- confirming ----------

    @Test
    void confirmingSwitchesTheEmailMarksItVerifiedAndConsumesTheToken() {
        EmailVerification pending = pending("new@example.com", NOW.plus(Duration.ofHours(1)));
        when(verifications.findByTokenHash(ProfileService.hash("the-token"))).thenReturn(Optional.of(pending));

        service.confirmEmail("the-token");

        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.isEmailVerified()).isTrue();
        verify(verifications).delete(pending);
    }

    @Test
    void aTokenIsExpiredFromTheMomentItsLifetimeEnds() {
        EmailVerification pending = pending("new@example.com", NOW);   // expires exactly now
        when(verifications.findByTokenHash(ProfileService.hash("t"))).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.confirmEmail("t")).isInstanceOf(InvalidVerificationTokenException.class);

        assertThat(user.getEmail()).isEqualTo("old@example.com");
    }

    @Test
    void aTokenStillValidForAnotherNanosecondWorks() {
        EmailVerification pending = pending("new@example.com", NOW.plusNanos(1));
        when(verifications.findByTokenHash(ProfileService.hash("t"))).thenReturn(Optional.of(pending));

        service.confirmEmail("t");

        assertThat(user.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void anUnknownTokenIsRejected() {
        when(verifications.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmEmail("nope")).isInstanceOf(InvalidVerificationTokenException.class);
    }

    @Test
    void ifTheEmailWasTakenInTheMeantimeTheConfirmationFails() {
        EmailVerification pending = pending("new@example.com", NOW.plus(Duration.ofHours(1)));
        when(verifications.findByTokenHash(ProfileService.hash("t"))).thenReturn(Optional.of(pending));
        when(users.existsByEmail("new@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.confirmEmail("t")).isInstanceOf(ConflictException.class);

        assertThat(user.getEmail()).isEqualTo("old@example.com");
    }

    // ---------- helpers ----------

    private EmailVerification pending(String newEmail, Instant expiresAt) {
        return new EmailVerification(7L, newEmail, "irrelevant", NOW.minusSeconds(3600), expiresAt);
    }

    private static long eq7() {
        return org.mockito.ArgumentMatchers.eq(7L);
    }

    private User existingUser() {
        User created = new User("old@example.com", "hash", Role.USER, NOW.minusSeconds(86_400));
        ReflectionTestUtils.setField(created, "id", 7L);
        when(users.findById(7L)).thenReturn(Optional.of(created));
        when(verifications.save(any(EmailVerification.class))).thenAnswer(returnsFirstArg());
        return created;
    }
}
