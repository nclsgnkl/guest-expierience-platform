package de.gxp.auth.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TokenStore} (REQ-327) with a mutable clock — idle
 * expiry and sliding are asserted without waiting.
 */
class TokenStoreTest {

    /** Settable clock so tests control the passage of time. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-08-19T12:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final TokenStore store = new TokenStore(clock, 600);

    @Test
    void issuedTokenSlidesAndReturnsTheNewExpiry() {
        TokenStore.IssuedToken issued = store.issue();
        assertEquals(clock.instant().plusSeconds(600), issued.expiresAt());

        clock.advance(Duration.ofSeconds(500));
        Instant slid = store.slide(TokenStore.sha256Hex(issued.rawToken()));
        assertNotNull(slid);
        // the expiry moved forward: now + 600, not the original expiry
        assertEquals(clock.instant().plusSeconds(600), slid);
        assertTrue(slid.isAfter(issued.expiresAt()));
    }

    @Test
    void slidingKeepsATokenAliveBeyondTheOriginalWindow() {
        TokenStore.IssuedToken issued = store.issue();
        String hash = TokenStore.sha256Hex(issued.rawToken());
        for (int i = 0; i < 3; i++) {
            clock.advance(Duration.ofSeconds(500)); // each within the window
            assertNotNull(store.slide(hash));
        }
        // 1500 s after issue — alive only because every hit slid the expiry
    }

    @Test
    void idledOutTokenIsRejectedAndDropped() {
        TokenStore.IssuedToken issued = store.issue();
        String hash = TokenStore.sha256Hex(issued.rawToken());
        clock.advance(Duration.ofSeconds(601));
        assertNull(store.slide(hash));
        // and it stays dead even if time went backwards conceptually — entry gone
        assertNull(store.slide(hash));
    }

    @Test
    void storeNeverContainsTheRawToken() {
        TokenStore.IssuedToken issued = store.issue();
        // the raw value is not a key: presenting it as if it were the stored
        // hash misses; only its SHA-256 hits (REQ-327: hashes only)
        assertNull(store.slide(issued.rawToken()));
        assertNotNull(store.slide(TokenStore.sha256Hex(issued.rawToken())));
    }

    @Test
    void unknownTokenIsRejected() {
        assertNull(store.slide(TokenStore.sha256Hex("never-issued")));
    }

    @Test
    void removeKillsExactlyThatToken() {
        TokenStore.IssuedToken keep = store.issue();
        TokenStore.IssuedToken drop = store.issue();
        store.remove(TokenStore.sha256Hex(drop.rawToken()));
        assertNull(store.slide(TokenStore.sha256Hex(drop.rawToken())));
        assertNotNull(store.slide(TokenStore.sha256Hex(keep.rawToken())));
    }

    @Test
    void clearKillsEveryToken() {
        TokenStore.IssuedToken a = store.issue();
        TokenStore.IssuedToken b = store.issue();
        store.clear();
        assertNull(store.slide(TokenStore.sha256Hex(a.rawToken())));
        assertNull(store.slide(TokenStore.sha256Hex(b.rawToken())));
    }

    @Test
    void issuePurgesExpiredEntriesLazily() {
        TokenStore.IssuedToken old = store.issue();
        clock.advance(Duration.ofSeconds(700));
        store.issue(); // triggers the lazy purge
        assertNull(store.slide(TokenStore.sha256Hex(old.rawToken())));
    }

    @Test
    void rawTokensAreBase64UrlWithoutPadding() {
        TokenStore.IssuedToken issued = store.issue();
        // 32 bytes -> 43 base64url chars, no '=', no '+', no '/'
        assertEquals(43, issued.rawToken().length());
        assertTrue(issued.rawToken().matches("[A-Za-z0-9_-]{43}"));
    }
}
