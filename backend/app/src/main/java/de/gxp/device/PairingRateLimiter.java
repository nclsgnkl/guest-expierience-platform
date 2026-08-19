package de.gxp.device;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

import de.gxp.api.model.ApiError;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Rate limit for the ONE spammable unauthenticated write (REQ-409): pairing
 * create, max 5 per 60 s, global — a building pairs a handful of devices
 * ever. The {@code LoginRateLimiter} pattern: in-memory, package-private,
 * answered as 429 with {@code Retry-After}. Poll needs none: without the
 * 256-bit secret every answer is 404.
 */
@ApplicationScoped
class PairingRateLimiter {

    static final int MAX_CREATES_PER_WINDOW = 5;
    static final Duration WINDOW = Duration.ofSeconds(60);

    private final Clock clock;
    private final Deque<Instant> createTimes = new ArrayDeque<>();

    PairingRateLimiter() {
        this(Clock.systemUTC());
    }

    PairingRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Admits one create or throws the 429 (with {@code Retry-After}). */
    synchronized void acquire() {
        Instant now = clock.instant();
        Instant windowStart = now.minus(WINDOW);
        while (!createTimes.isEmpty() && !createTimes.peekFirst().isAfter(windowStart)) {
            createTimes.removeFirst();
        }
        if (createTimes.size() >= MAX_CREATES_PER_WINDOW) {
            long retryAfterSeconds = Math.max(1,
                    Duration.between(windowStart, createTimes.peekFirst()).plusMillis(999).toSeconds());
            throw new WebApplicationException(Response.status(429)
                    .header("Retry-After", retryAfterSeconds)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ApiError().code("pairing-rate-limited")
                            .message("Too many pairing requests — try again later"))
                    .build());
        }
        createTimes.addLast(now);
    }

    /** Test hook (same package): the suite shares one instance. */
    synchronized void reset() {
        createTimes.clear();
    }
}
