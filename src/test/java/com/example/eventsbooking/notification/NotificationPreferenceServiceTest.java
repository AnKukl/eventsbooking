package com.example.eventsbooking.notification;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationPreferenceServiceTest {

    private final NotificationPreferenceRepository repository = mock(NotificationPreferenceRepository.class);
    private final NotificationPreferenceService service = new NotificationPreferenceService(repository);

    @Test
    void everyTypeIsListedInEnumOrderWithDefaultsWhenNothingWasStored() {
        when(repository.findByUserId(7L)).thenReturn(List.of());

        List<NotificationPreference> preferences = service.get(7L);

        assertThat(preferences).extracting(NotificationPreference::type).containsExactly(NotificationType.values());
        assertThat(preferences).allSatisfy(p -> {
            assertThat(p.email()).isTrue();
            assertThat(p.push()).isTrue();
        });
    }

    @Test
    void storedChoicesOverrideTheDefaultsForTheirTypeOnly() {
        when(repository.findByUserId(7L)).thenReturn(
                List.of(new NotificationPreference(NotificationType.SEAT_AVAILABLE, false, true)));

        List<NotificationPreference> preferences = service.get(7L);

        assertThat(preferences).filteredOn(p -> p.type() == NotificationType.SEAT_AVAILABLE)
                .containsExactly(new NotificationPreference(NotificationType.SEAT_AVAILABLE, false, true));
        assertThat(preferences).filteredOn(p -> p.type() != NotificationType.SEAT_AVAILABLE)
                .allSatisfy(p -> assertThat(p.email() && p.push()).isTrue());
    }

    @Test
    void updateStoresEachGivenTypeForTheUserAndReturnsTheMergedView() {
        NotificationPreference change = new NotificationPreference(NotificationType.REMINDER_2H, false, false);
        when(repository.findByUserId(7L)).thenReturn(List.of(change));

        List<NotificationPreference> result = service.update(7L, List.of(change));

        verify(repository).upsert(7L, change);
        assertThat(result).contains(change).hasSize(NotificationType.values().length);
    }
}
