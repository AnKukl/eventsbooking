package com.example.eventsbooking.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record NotificationPreferencesRequest(
        @NotEmpty @Size(max = 4) List<@Valid @NotNull Item> preferences) {

    public record Item(@NotNull NotificationType type, @NotNull Boolean email, @NotNull Boolean push) {

        NotificationPreference toPreference() {
            return new NotificationPreference(type, email, push);
        }
    }

    /** Two entries for the same type would be ambiguous. */
    @AssertTrue(message = "each notification type may appear only once")
    public boolean isTypesUnique() {
        return preferences == null
                || preferences.stream().filter(item -> item != null).map(Item::type).distinct().count()
                        == preferences.stream().filter(item -> item != null).count();
    }
}
