package com.example.eventsbooking.organizer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class OrganizerAnalyticsService {

    static final int MAX_DAYS = 365;
    private static final String DEFAULT_RANGE = "7d";
    private static final Pattern RANGE = Pattern.compile("([1-9][0-9]{0,2})d");

    private final AnalyticsRepository repository;
    private final Clock clock;

    public OrganizerAnalyticsService(AnalyticsRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Totals over the last {@code range} days (for example {@code 7d}; 1 to 365), for the caller's events only. */
    public AnalyticsResponse analytics(long organizerId, String range) {
        String effective = range == null ? DEFAULT_RANGE : range;
        Matcher matcher = RANGE.matcher(effective);
        if (!matcher.matches() || Integer.parseInt(matcher.group(1)) > MAX_DAYS) {
            throw new InvalidRangeException();
        }
        Instant to = clock.instant();
        Instant from = to.minus(Duration.ofDays(Integer.parseInt(matcher.group(1))));

        AnalyticsRepository.Aggregates totals = repository.aggregate(organizerId, from, to);
        BigDecimal occupancy = totals.capacity() == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(totals.confirmedSeats()).divide(BigDecimal.valueOf(totals.capacity()), 4,
                        RoundingMode.HALF_UP);
        return new AnalyticsResponse(effective, from, to, totals.sold(), totals.revenue(), occupancy);
    }
}
