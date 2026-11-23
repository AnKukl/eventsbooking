package com.example.eventsbooking.user;

import com.example.eventsbooking.common.TooManyRequestsException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force brake for the authentication endpoints (Bucket4j, in memory). Every endpoint has a bucket per client
 * address and, where the request names one, a bucket per email, so neither one address trying many accounts nor many
 * addresses trying one account get far. Time comes from the application {@link Clock}.
 */
@Component
public class AuthRateLimiter {

    /** Above this many buckets the idle ones are dropped, so random addresses cannot grow the map without bound. */
    static final int SWEEP_THRESHOLD = 10_000;

    private record Entry(Bucket bucket, Instant lastUsed) {
    }

    private final RateLimitProperties properties;
    private final Clock clock;
    private final TimeMeter time;
    private final Map<String, Entry> buckets = new ConcurrentHashMap<>();

    public AuthRateLimiter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.time = new TimeMeter() {
            @Override
            public long currentTimeNanos() {
                Instant now = clock.instant();
                return now.getEpochSecond() * 1_000_000_000L + now.getNano();
            }

            @Override
            public boolean isWallClockBased() {
                return true;
            }
        };
    }

    /** Counts one attempt; throws 429 with the time to wait if the address or the email has used up its limit. */
    public void check(String endpoint, String clientAddress, String email) {
        consume(endpoint + ":ip:" + clientAddress);
        if (email != null && !email.isBlank()) {
            consume(endpoint + ":email:" + email.strip().toLowerCase(Locale.ROOT));
        }
    }

    private void consume(String key) {
        Instant now = clock.instant();
        if (buckets.size() > SWEEP_THRESHOLD) {
            Instant idleSince = now.minus(properties.period());
            buckets.values().removeIf(entry -> entry.lastUsed().isBefore(idleSince));
        }
        Entry entry = buckets.compute(key, (k, old) -> new Entry(old == null ? newBucket() : old.bucket(), now));
        ConsumptionProbe probe = entry.bucket().tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw new TooManyRequestsException(Duration.ofNanos(probe.getNanosToWaitForRefill()));
        }
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(properties.attempts())
                        .refillIntervally(properties.attempts(), properties.period()))
                .withCustomTimePrecision(time)
                .build();
    }
}
