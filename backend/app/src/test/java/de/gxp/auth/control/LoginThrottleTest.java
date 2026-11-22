package de.gxp.auth.control;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.WebApplicationException;

/**
 * Unit — the per-address budget with a fake clock (REQ-601, REQ-602,
 * REQ-605; design D2/D3/D21). Production numbers (60 s base) are used here;
 * nothing sleeps.
 */
class LoginThrottleTest {

    private static final String A = "203.0.113.1";
    private static final String B = "203.0.113.2";

    /** A settable clock so escalation is observed without waiting. */
    private static final class FakeClock extends Clock {
        Instant now = Instant.parse("2026-08-25T10:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }
    }

    private final FakeClock clock = new FakeClock();
    private final LoginThrottle throttle = new LoginThrottle(clock, 60);

    private static WebApplicationException blocked(LoginThrottle throttle, String address) {
        return assertThrows(WebApplicationException.class, () -> throttle.check(address));
    }

    private static long retryAfter(WebApplicationException e) {
        return Long.parseLong(e.getResponse().getHeaderString("Retry-After"));
    }

    @Test
    void fiveFailuresBlockTheSixthWith429AndRetryAfter() {
        for (int i = 0; i < 4; i++) {
            throttle.recordFailure(A);
            assertDoesNotThrow(() -> throttle.check(A), "four failures are still inside the budget");
        }
        throttle.recordFailure(A);
        WebApplicationException e = blocked(throttle, A);
        assertEquals(429, e.getResponse().getStatus());
        assertEquals(60, retryAfter(e));
        assertEquals(5, throttle.failuresOf(A));
    }

    @Test
    void anotherAddressIsUnaffected() {
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure(A);
        }
        blocked(throttle, A);
        assertDoesNotThrow(() -> throttle.check(B));
        assertEquals(0, throttle.failuresOf(B));
    }

    @Test
    void aSuccessClearsTheAddress() {
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure(A);
        }
        throttle.recordSuccess(A);
        assertDoesNotThrow(() -> throttle.check(A));
        assertEquals(0, throttle.failuresOf(A));
    }

    @Test
    void theBlockExpiresAndTheWindowResets() {
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure(A);
        }
        clock.advance(Duration.ofSeconds(60));
        assertDoesNotThrow(() -> throttle.check(A), "Retry-After elapsed: admitted again");
        clock.advance(Duration.ofMinutes(15));
        throttle.recordFailure(A);
        assertEquals(1, throttle.failuresOf(A), "a failure after the 15-minute window starts a fresh count");
    }

    @Test
    void escalationDoublesPerFurtherFailureAndCapsAt900Seconds() {
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure(A);
        }
        assertEquals(60, retryAfter(blocked(throttle, A)));
        throttle.recordFailure(A);
        assertEquals(120, retryAfter(blocked(throttle, A)));
        throttle.recordFailure(A);
        assertEquals(240, retryAfter(blocked(throttle, A)));
        throttle.recordFailure(A);
        assertEquals(480, retryAfter(blocked(throttle, A)));
        throttle.recordFailure(A);
        assertEquals(900, retryAfter(blocked(throttle, A)), "960 would exceed the cap");
        for (int i = 0; i < 40; i++) {
            throttle.recordFailure(A);
        }
        assertEquals(900, retryAfter(blocked(throttle, A)), "no overflow, still the cap");
    }

    @Test
    void retryAfterCountsDownAndIsNeverZero() {
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure(A);
        }
        clock.advance(Duration.ofMillis(59_500));
        assertEquals(1, retryAfter(blocked(throttle, A)));
    }

    @Test
    void twentyThousandAddressesNeverExceedTheCap() {
        for (int i = 0; i < 20_000; i++) {
            throttle.recordFailure("10." + (i >> 16 & 255) + "." + (i >> 8 & 255) + "." + (i & 255));
            assertTrue(throttle.trackedAddresses() <= LoginThrottle.MAX_TRACKED,
                    "the map must be bounded at insert time");
        }
        assertEquals(LoginThrottle.MAX_TRACKED, throttle.trackedAddresses());
    }

    @Test
    void staleBucketsAreEvictedBeforeLiveOnes() {
        throttle.recordFailure(A);
        clock.advance(Duration.ofHours(2));
        for (int i = 0; i < LoginThrottle.MAX_TRACKED; i++) {
            throttle.recordFailure("10.1." + (i >> 8 & 255) + "." + (i & 255));
        }
        assertEquals(0, throttle.failuresOf(A), "the > 1 h stale bucket went first");
        assertEquals(LoginThrottle.MAX_TRACKED, throttle.trackedAddresses());
    }

    @Test
    void anUnknownAddressPassesWithoutCreatingState() {
        assertDoesNotThrow(() -> throttle.check(A));
        assertEquals(0, throttle.trackedAddresses());
    }

    // --- bucket key (D2 amendment 2026-08-26: IPv6 keyed by /64) -----------

    @Test
    void ipv4AddressesAreTheirOwnBuckets() {
        assertEquals("203.0.113.1", LoginThrottle.bucketKey("203.0.113.1"));
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure(A);
        }
        assertDoesNotThrow(() -> throttle.check("203.0.113.3"));
    }

    @Test
    void ipv6AddressesInTheSameSlash64ShareOneBucket() {
        String first = "2001:db8:1:2:aaaa:bbbb:cccc:dddd";
        String rotated = "2001:db8:1:2:1111:2222:3333:4444";
        assertEquals("2001:db8:1:2:0:0:0:0/64", LoginThrottle.bucketKey(first));
        assertEquals(LoginThrottle.bucketKey(first), LoginThrottle.bucketKey(rotated));
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure(first);
        }
        blocked(throttle, rotated);
        assertEquals(5, throttle.failuresOf(rotated));
        throttle.recordSuccess(rotated);
        assertEquals(0, throttle.failuresOf(first));
    }

    @Test
    void ipv6AddressesInDifferentSlash64sAreSeparate() {
        String a = "2001:db8:1:2::1";
        String other = "2001:db8:1:3::1";
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure(a);
        }
        blocked(throttle, a);
        assertDoesNotThrow(() -> throttle.check(other));
        assertEquals(0, throttle.failuresOf(other));
    }

    @Test
    void ipv4MappedIpv6IsTheIpv4Address() {
        assertEquals("192.0.2.7", LoginThrottle.bucketKey("::ffff:192.0.2.7"));
        throttle.recordFailure("::ffff:192.0.2.7");
        assertEquals(1, throttle.failuresOf("192.0.2.7"));
    }

    @Test
    void aScopedOrUnparseableAddressNeverThrows() {
        assertEquals("fe80:0:0:0:0:0:0:0/64", LoginThrottle.bucketKey("fe80::1%eth0"));
        assertEquals("not:an:address:zz", LoginThrottle.bucketKey("not:an:address:zz"));
        assertDoesNotThrow(() -> throttle.recordFailure("not:an:address:zz"));
    }
}
