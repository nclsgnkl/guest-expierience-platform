package de.gxp.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.gxp.infrastructure.testsupport.LogSweep;
import de.gxp.infrastructure.testsupport.ManagerFixtures;
import de.gxp.infrastructure.testsupport.TrustedProxyProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * REQ-603 / REQ-601 under {@link TrustedProxyProfile}: the loopback peer is
 * the configured trusted proxy, so the standard {@code Forwarded} header is
 * honoured — attributed address and via-proxy flag — and two forwarded
 * addresses exhaust two separate throttle buckets. The default-profile
 * counterpart ({@code de.gxp.infrastructure.http.ClientAttributionTest})
 * proves the same headers are ignored when no proxy is configured.
 * Since T-10 every login in this profile is via-proxy, so a code-less
 * correct password is {@code 401 second-factor-required} (REQ-617) — the
 * second-factor login matrix itself is the user feature's proxy-profile
 * test (same profile, no other feature's internals are named here — the
 * import guard is textual).
 *
 * <p>No throttle reset is needed here: this profile boots its own instance,
 * and every test uses addresses of its own.
 *
 * <p>Log sweep (REQ-605, widened 2026-08-26): {@link LogSweep} over
 * {@code de.gxp} + {@code io.quarkus.vertx.http}. This is the profile where
 * Quarkus actually has something to say — the one line it emits on this path
 * is a value-free {@code WARN} that a client-sent
 * {@code X-Forwarded-Trusted-Proxy} was overwritten (verified against the
 * 3.33.3.1 jar in the T-01…T-04 review). Every test asserts that no record
 * mentions a forwarded address, and every test except the deliberate
 * header-overwrite one asserts that nothing was logged at all.
 */
@QuarkusTest
@TestProfile(TrustedProxyProfile.class)
class TrustedProxyLoginTest {

    private static final LogSweep SWEEP = new LogSweep();
    private static final List<String> ADDRESSES_USED = List.of("203.0.113", "198.51.100", "1.2.3.4", "_hidden");

    @BeforeAll
    static void captureLogs() {
        SWEEP.start();
    }

    @AfterAll
    static void stopCapturing() {
        SWEEP.stop();
    }

    @BeforeEach
    void clearSweep() {
        SWEEP.clear();
    }

    @AfterEach
    void noAddressWasLogged() {
        for (LogRecord record : SWEEP.records()) {
            String text = String.valueOf(record.getMessage()) + " " + java.util.Arrays.toString(record.getParameters());
            for (String address : ADDRESSES_USED) {
                assertFalse(text.contains(address), "a forwarded address reached a log line (REQ-605):\n" + SWEEP.describe());
            }
        }
    }

    private void assertNothingLogged() {
        assertTrue(SWEEP.records().isEmpty(), "nothing may be logged on this path (REQ-605):\n" + SWEEP.describe());
    }

    /** Carries a (wrong) code so the attempt is verified and counted — a code-less one is refused unverified since T-10. */
    private static io.restassured.response.Response attemptFrom(String address, String password) {
        return given().header("Forwarded", "for=" + address)
                .contentType("application/json")
                .body(Map.of("username", ManagerFixtures.USERNAME, "password", password, "totpCode", "000000"))
                .when().post("/api/manager/login");
    }

    @Test
    void theForwardedAddressIsAttributedAndFlaggedViaProxy() {
        given().header("Forwarded", "for=203.0.113.9")
                .when().get("/test/client-attribution")
                .then().statusCode(200)
                .body("address", equalTo("203.0.113.9"))
                .body("viaTrustedProxy", equalTo(true));
        assertNothingLogged();
    }

    /**
     * Pins WHY the relay must overwrite, never append (review 2026-08-26,
     * finding #1; {@code docs/backend/remote-operations.md} §7 "Relay
     * obligation"): Quarkus 3.33.3.1 honours the FIRST {@code for=} element
     * of a multi-hop {@code Forwarded} header. A relay that appended its
     * element to a client-supplied one would let the client choose its own
     * attribution. Should a Quarkus upgrade flip this to "last element", the
     * test fails and the obligation must be re-derived — not silently kept.
     */
    @Test
    void theFirstForwardedElementWinsSoTheRelayMustReplaceNotAppend() {
        given().header("Forwarded", "for=1.2.3.4, for=203.0.113.9")
                .when().get("/test/client-attribution")
                .then().statusCode(200)
                .body("address", equalTo("1.2.3.4"))
                .body("viaTrustedProxy", equalTo(true));
        assertNothingLogged();
    }

    /**
     * An RFC 7239 obfuscated identifier ({@code for=_hidden}) has no
     * {@code hostAddress()}; before 2026-08-26 that was a {@code null}
     * throttle key and a bare 500 from the login endpoint. It is now a coded
     * {@code 400} with no throttle state and no PBKDF2 work — never a shared
     * bucket and never a silent downgrade to "not via proxy".
     */
    @Test
    void anUnparseableForwardedAddressIsA400NotA500() {
        given().header("Forwarded", "for=_hidden")
                .when().get("/test/client-attribution")
                .then().statusCode(400)
                .body("code", equalTo("unattributable-address"));
        given().header("Forwarded", "for=_hidden")
                .contentType("application/json")
                .body(Map.of("username", ManagerFixtures.USERNAME, "password", ManagerFixtures.PASSWORD))
                .when().post("/api/manager/login")
                .then().statusCode(400)
                .body("code", equalTo("unattributable-address"));
        assertNothingLogged();
    }

    @Test
    void aClientSentTrustedProxyHeaderIsOverwrittenByTheProxyStamp() {
        // X-Forwarded-Trusted-Proxy is stamped by Quarkus for a trusted peer
        // and stripped otherwise; the client behind the proxy cannot forge it
        // because the proxy's own stamping overwrites whatever arrived. (The
        // untrusted-peer negative lives in UntrustedPeerAttributionTest.)
        // Quarkus logs a value-free WARN about the overwrite here — the only
        // test in this class that may log at all; the @AfterEach still proves
        // no address is in it.
        given().header("Forwarded", "for=203.0.113.10")
                .header("X-Forwarded-Trusted-Proxy", "false")
                .when().get("/test/client-attribution")
                .then().statusCode(200)
                .body("address", equalTo("203.0.113.10"))
                .body("viaTrustedProxy", equalTo(true));
    }

    /**
     * Planner's verification note (03-todo T-04), answered empirically
     * against Quarkus 3.33.3.1 on 2026-08-25: <b>yes</b> — a trusted peer
     * that sends NO {@code Forwarded} header at all is still stamped
     * {@code X-Forwarded-Trusted-Proxy: true}. The stamp means "the socket
     * peer is a trusted proxy", not "an address was forwarded"; the
     * attributed address then stays the peer's own. Production semantics are
     * unaffected — only the relay is ever the trusted peer, and it always
     * forwards the originating address — but a test in this profile that
     * omits the header must expect {@code viaTrustedProxy=true}.
     */
    @Test
    void aTrustedPeerWithoutAForwardedHeaderIsAttributedToItself() {
        given().when().get("/test/client-attribution")
                .then().statusCode(200)
                .body("address", equalTo("127.0.0.1"))
                .body("viaTrustedProxy", equalTo(true));
        assertNothingLogged();
    }

    @Test
    void twoForwardedAddressesHaveSeparateBuckets() {
        String a = "198.51.100.1";
        String b = "198.51.100.2";
        for (int i = 0; i < 5; i++) {
            attemptFrom(a, "wrong-" + i).then().statusCode(401).body("code", equalTo("invalid-credentials"));
        }
        attemptFrom(a, ManagerFixtures.PASSWORD).then()
                .statusCode(429)
                .header("Retry-After", notNullValue())
                .body("code", equalTo("too-many-attempts"));
        // B — same proxy, different forwarded address — is admitted at once:
        // not 429 but the generic 401 (admin has no factor, REQ-617/D7)
        attemptFrom(b, ManagerFixtures.PASSWORD).then().statusCode(401).body("code", equalTo("invalid-credentials"));
        // and the proxy's own address (no Forwarded) is a third bucket
        ManagerFixtures.attempt(ManagerFixtures.USERNAME, ManagerFixtures.PASSWORD)
                .then().statusCode(401).body("code", equalTo("second-factor-required"));
        assertNothingLogged();
    }
}
