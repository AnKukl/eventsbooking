package com.example.eventsbooking.organizer;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrganizerAnalyticsServiceTest {

    private static final Instant NOW = Instant.parse("2030-03-01T12:00:00Z");

    private final AnalyticsRepository repository = mock(AnalyticsRepository.class);
    private final OrganizerAnalyticsService service =
            new OrganizerAnalyticsService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void theRangeEndsNowAndStartsThatManyDaysEarlier() {
        when(repository.aggregate(7L, NOW.minus(Duration.ofDays(7)), NOW))
                .thenReturn(new AnalyticsRepository.Aggregates(12, new BigDecimal("240.00"), 100, 25));

        AnalyticsResponse response = service.analytics(7L, "7d");

        assertThat(response.range()).isEqualTo("7d");
        assertThat(response.from()).isEqualTo(NOW.minus(Duration.ofDays(7)));
        assertThat(response.to()).isEqualTo(NOW);
        assertThat(response.sold()).isEqualTo(12);
        assertThat(response.revenue()).isEqualByComparingTo("240.00");
        assertThat(response.occupancy()).isEqualByComparingTo("0.25");
    }

    @Test
    void occupancyIsZeroWhenThereIsNoCapacity() {
        when(repository.aggregate(any(Long.class), any(), any()))
                .thenReturn(new AnalyticsRepository.Aggregates(0, BigDecimal.ZERO, 0, 0));

        assertThat(service.analytics(7L, "30d").occupancy()).isEqualByComparingTo("0");
    }

    @Test
    void aMissingRangeMeansSevenDays() {
        when(repository.aggregate(any(Long.class), any(), any()))
                .thenReturn(new AnalyticsRepository.Aggregates(0, BigDecimal.ZERO, 0, 0));

        assertThat(service.analytics(7L, null).range()).isEqualTo("7d");
        verify(repository).aggregate(7L, NOW.minus(Duration.ofDays(7)), NOW);
    }

    @Test
    void theLongestRangeIsAYear() {
        when(repository.aggregate(any(Long.class), any(), any()))
                .thenReturn(new AnalyticsRepository.Aggregates(0, BigDecimal.ZERO, 0, 0));

        assertThat(service.analytics(7L, "365d").range()).isEqualTo("365d");
    }

    @Test
    void aRangeThatIsNotADayCountIsRejectedBeforeTheDatabaseIsTouched() {
        for (String range : new String[] {"366d", "0d", "-3d", "7", "d", "7days", "abc", "7d;drop", " 7d", "07d", "1000d"}) {
            assertThatThrownBy(() -> service.analytics(7L, range)).as(range).isInstanceOf(InvalidRangeException.class);
        }
        verifyNoInteractions(repository);
    }
}
