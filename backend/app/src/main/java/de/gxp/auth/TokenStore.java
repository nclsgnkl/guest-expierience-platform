package de.gxp.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * In-memory sliding bearer-token store (REQ-327). Deliberately in-memory: a
 * restart logs everyone out, which is acceptable and desirable (one instance,
 * one admin). Holds SHA-256 hex hashes of tokens, NEVER the raw value; the
 * raw token exists exactly once, in the login response.
 *
 * <p>Pinned parameters: 32-byte {@link SecureRandom} tokens (base64url, no
 * padding), 600 s idle expiry slid by every authenticated request. The idle
 * timeout is configurable only for the test profile.
 *
 * <p>This class logs nothing (constitution principle 3).
 */
@ApplicationScoped
class TokenStore {

    /** A freshly issued token — the only place the raw value ever appears. */
    record IssuedToken(String rawToken, Instant expiresAt) {
    }

    private final ConcurrentHashMap<String, Instant> expiryByTokenHash = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final Duration idleTimeout;

    @Inject
    TokenStore(@ConfigProperty(name = "gxp.auth.idle-timeout-seconds", defaultValue = "600") long idleTimeoutSeconds) {
        this(Clock.systemUTC(), idleTimeoutSeconds);
    }

    TokenStore(Clock clock, long idleTimeoutSeconds) {
        this.clock = clock;
        this.idleTimeout = Duration.ofSeconds(idleTimeoutSeconds);
    }

    long idleTimeoutSeconds() {
        return idleTimeout.toSeconds();
    }

    /** Issues a fresh 256-bit token; only its SHA-256 is retained. Purges lazily. */
    IssuedToken issue() {
        purgeExpired();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = clock.instant().plus(idleTimeout);
        expiryByTokenHash.put(sha256Hex(raw), expiresAt);
        return new IssuedToken(raw, expiresAt);
    }

    /**
     * Authenticated hit: slides the idle expiry forward and returns the new
     * expiry, or {@code null} for an unknown or idled-out token (the expired
     * entry is dropped).
     */
    Instant slide(String tokenSha256Hex) {
        Instant now = clock.instant();
        Instant slid = expiryByTokenHash.computeIfPresent(tokenSha256Hex,
                (hash, expiry) -> expiry.isAfter(now) ? now.plus(idleTimeout) : null);
        return slid;
    }

    /** Server-side drop of one token (logout). */
    void remove(String tokenSha256Hex) {
        expiryByTokenHash.remove(tokenSha256Hex);
    }

    /** Drops every active token — every password change calls this (v1.5.1). */
    void clear() {
        expiryByTokenHash.clear();
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        expiryByTokenHash.values().removeIf(expiry -> !expiry.isAfter(now));
    }

    static String sha256Hex(String rawToken) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
