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
        TokenStore.IssuedToken issued = store.issue("admin");
        assertEquals(clock.instant().plusSeconds(600), issued.expiresAt());

        clock.advance(Duration.ofSeconds(500));
        TokenStore.Session slid = store.slide(TokenStore.sha256Hex(issued.rawToken()));
        assertNotNull(slid);
        // the expiry moved forward: now + 600, not the original expiry
        assertEquals(clock.instant().plusSeconds(600), slid.expiry());
        assertTrue(slid.expiry().isAfter(issued.expiresAt()));
        assertEquals("admin", slid.username(), "the session knows whose it is (REQ-612)");
    }

    @Test
    void slidingKeepsATokenAliveBeyondTheOriginalWindow() {
        TokenStore.IssuedToken issued = store.issue("admin");
        String hash = TokenStore.sha256Hex(issued.rawToken());
        for (int i = 0; i < 3; i++) {
            clock.advance(Duration.ofSeconds(500)); // each within the window
            assertNotNull(store.slide(hash));
        }
        // 1500 s after issue — alive only because every hit slid the expiry
    }

    @Test
    void idledOutTokenIsRejectedAndDropped() {
        TokenStore.IssuedToken issued = store.issue("admin");
        String hash = TokenStore.sha256Hex(issued.rawToken());
        clock.advance(Duration.ofSeconds(601));
        assertNull(store.slide(hash));
        // and it stays dead even if time went backwards conceptually — entry gone
        assertNull(store.slide(hash));
    }

    @Test
    void storeNeverContainsTheRawToken() {
        TokenStore.IssuedToken issued = store.issue("admin");
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
        TokenStore.IssuedToken keep = store.issue("admin");
        TokenStore.IssuedToken drop = store.issue("admin");
        store.remove(TokenStore.sha256Hex(drop.rawToken()));
        assertNull(store.slide(TokenStore.sha256Hex(drop.rawToken())));
        assertNotNull(store.slide(TokenStore.sha256Hex(keep.rawToken())));
    }

    @Test
    void removeAllOfKillsEverySessionOfThatUserAndNobodyElses() {
        // REQ-611 / D13: a password change or deactivation hits ONE user
        TokenStore.IssuedToken adminOne = store.issue("admin");
        TokenStore.IssuedToken adminTwo = store.issue("admin");
        TokenStore.IssuedToken other = store.issue("other");
        store.removeAllOf("admin");
        assertNull(store.slide(TokenStore.sha256Hex(adminOne.rawToken())));
        assertNull(store.slide(TokenStore.sha256Hex(adminTwo.rawToken())));
        TokenStore.Session survivor = store.slide(TokenStore.sha256Hex(other.rawToken()));
        assertNotNull(survivor, "another user's session is not that credential");
        assertEquals("other", survivor.username());
        assertEquals(1, store.size());
    }

    @Test
    void removeAllOfAnUnknownUserIsANoOp() {
        store.issue("admin");
        store.removeAllOf("nobody");
        assertEquals(1, store.size());
    }

    /**
     * The scheduled purge (constitution v1.7.0) actually SHRINKS the map.
     *
     * <p>Rewritten 2026-08-25 from {@code issuePurgesExpiredEntriesLazily},
     * which asserted only that {@code slide(...)} returned {@code null} for
     * the stale token — true whether or not anything had been purged, because
     * {@code slide} rejects an expired entry by itself. It therefore tested
     * the purge not at all. The map size is the only observable that tells
     * the two apart, hence {@link TokenStore#size()}.
     */
    @Test
    void purgeExpiredDropsIdledOutEntries() {
        TokenStore.IssuedToken stale = store.issue("admin");
        clock.advance(Duration.ofSeconds(700));
        TokenStore.IssuedToken fresh = store.issue("admin");
        assertEquals(2, store.size(), "both entries are still in the map — nothing purges on issue any more");

        store.purgeExpired();

        assertEquals(1, store.size(), "the idled-out entry must be GONE from the map, not merely rejected");
        assertNull(store.slide(TokenStore.sha256Hex(stale.rawToken())));
        assertNotNull(store.slide(TokenStore.sha256Hex(fresh.rawToken())), "the live token is untouched");
    }

    /**
     * The safety argument for removing the purge from {@code issue()}: a
     * lingering entry is never a usable credential, because {@code slide}
     * re-checks the expiry and drops it on the spot.
     */
    @Test
    void anUnpurgedExpiredEntryIsStillRefusedAndDropped() {
        TokenStore.IssuedToken issued = store.issue("admin");
        clock.advance(Duration.ofSeconds(700));
        assertEquals(1, store.size(), "the expired entry is still in the map until something purges it");

        assertNull(store.slide(TokenStore.sha256Hex(issued.rawToken())), "but it does not authenticate");
        assertEquals(0, store.size(), "and slide dropped it on the spot");
    }

    @Test
    void rawTokensAreBase64UrlWithoutPadding() {
        TokenStore.IssuedToken issued = store.issue("admin");
        // 32 bytes -> 43 base64url chars, no '=', no '+', no '/'
        assertEquals(43, issued.rawToken().length());
        assertTrue(issued.rawToken().matches("[A-Za-z0-9_-]{43}"));
    }
}
