package de.gxp.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import de.gxp.api.model.ApiError;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * The single brute-force choke point (REQ-326): 5 consecutive failures lock
 * the login endpoint for 30 s — answered as 429 with {@code Retry-After}.
 * Deliberately in-memory (one instance, one admin); a restart clears it, and
 * an attacker who can restart the box already owns it. The lock duration is
 * configurable only for the test profile.
 */
@ApplicationScoped
class LoginRateLimiter {

    private static final int MAX_CONSECUTIVE_FAILURES = 5;

    private final Clock clock;
    private final Duration lockDuration;

    private int consecutiveFailures;
    private Instant lockedUntil;

    @Inject
    LoginRateLimiter(@ConfigProperty(name = "gxp.auth.lockout-seconds", defaultValue = "30") long lockoutSeconds) {
        this(Clock.systemUTC(), lockoutSeconds);
    }

    LoginRateLimiter(Clock clock, long lockoutSeconds) {
        this.clock = clock;
        this.lockDuration = Duration.ofSeconds(lockoutSeconds);
    }

    /** Throws the 429 (with {@code Retry-After}) while the lock is active. */
    synchronized void checkNotLocked() {
        if (lockedUntil == null) {
            return;
        }
        Instant now = clock.instant();
        if (now.isBefore(lockedUntil)) {
            long retryAfterSeconds = Math.max(1, Duration.between(now, lockedUntil).plusMillis(999).toSeconds());
            throw new WebApplicationException(Response.status(429)
                    .header("Retry-After", retryAfterSeconds)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ApiError().code("login-locked")
                            .message("Too many failed login attempts — try again later"))
                    .build());
        }
        lockedUntil = null;
        consecutiveFailures = 0;
    }

    synchronized void recordFailure() {
        consecutiveFailures++;
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            lockedUntil = clock.instant().plus(lockDuration);
            consecutiveFailures = 0;
        }
    }

    synchronized void recordSuccess() {
        consecutiveFailures = 0;
        lockedUntil = null;
    }
}
