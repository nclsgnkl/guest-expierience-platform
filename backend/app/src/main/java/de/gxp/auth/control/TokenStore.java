package de.gxp.auth.control;

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
 * In-memory sliding bearer-token store (REQ-327; keyed by user since
 * auth-hardening REQ-611/REQ-612, D13). Deliberately in-memory: a restart
 * logs everyone out, which is acceptable and desirable (one instance). Holds
 * SHA-256 hex hashes of tokens, NEVER the raw value; the raw token exists
 * exactly once, in the login response. Each entry knows the username it was
 * issued for, so one user's sessions can be dropped without touching
 * anyone else's.
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

    /** What the store keeps per token hash: whose session it is, and until when. */
    record Session(String username, Instant expiry) {
    }

    private final ConcurrentHashMap<String, Session> sessionByTokenHash = new ConcurrentHashMap<>();
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

    /**
     * Issues a fresh 256-bit token; only its SHA-256 is retained.
     *
     * <p>It no longer purges expired entries first (constitution v1.7.0,
     * 2026-08-25): the purge is now time-driven, from
     * {@code de.gxp.infrastructure.scheduler}. <b>Safety of that removal:</b>
     * this only ever changed MEMORY behaviour, never an auth decision —
     * {@link #slide} re-checks the expiry itself and drops an idled-out entry
     * on the spot, so an entry that outlives its expiry in the map is already
     * dead as a credential.
     */
    IssuedToken issue(String username) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = clock.instant().plus(idleTimeout);
        sessionByTokenHash.put(sha256Hex(raw), new Session(username, expiresAt));
        return new IssuedToken(raw, expiresAt);
    }

    /**
     * Authenticated hit: slides the idle expiry forward and returns the
     * session with its new expiry, or {@code null} for an unknown or
     * idled-out token (the expired entry is dropped).
     */
    Session slide(String tokenSha256Hex) {
        Instant now = clock.instant();
        return sessionByTokenHash.computeIfPresent(tokenSha256Hex,
                (hash, session) -> session.expiry().isAfter(now)
                        ? new Session(session.username(), now.plus(idleTimeout))
                        : null);
    }

    /** Server-side drop of one token (logout). */
    void remove(String tokenSha256Hex) {
        sessionByTokenHash.remove(tokenSha256Hex);
    }

    /**
     * Drops every session of ONE user — the credential's owner on a password
     * change or reset, the account on deactivation (REQ-611, D13). Other
     * users' sessions are not that credential and stay. Replaces the
     * store-wide {@code clear()} of v1.5.1, which was written for one shared
     * password.
     */
    void removeAllOf(String username) {
        sessionByTokenHash.values().removeIf(session -> session.username().equals(username));
    }

    /**
     * Drops every idled-out entry. Package-private since 2026-08-25 so
     * {@link AuthControl#purgeExpiredTokens()} — the feature's one public
     * type — can offer it to the central scheduler. Memory hygiene only: an
     * entry still sitting here past its expiry is already refused by
     * {@link #slide}.
     */
    void purgeExpired() {
        Instant now = clock.instant();
        sessionByTokenHash.values().removeIf(session -> !session.expiry().isAfter(now));
    }

    /** Test probe (same package): how many entries the map currently holds. */
    int size() {
        return sessionByTokenHash.size();
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
