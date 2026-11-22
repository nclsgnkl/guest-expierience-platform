package de.gxp.device.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * The scheduled pairing sweep (constitution v1.7.0, owner decision
 * 2026-08-25): {@code DeviceControl#sweepExpiredPairingRequests} removes
 * exactly the rows whose TTL has run out, and leaves live ones alone.
 *
 * <p>The Control method is called DIRECTLY — the test never waits for the
 * scheduler to fire. The whole suite shares one Dev Services database and
 * runs with {@code quarkus.scheduler.start-mode=halted} for that reason;
 * a test that waited for 02:00 would also be a test that cannot fail fast.
 *
 * <p>Both rows are seeded and removed here, and the assertions are keyed on
 * the two seeded codes rather than on table counts: the sweep is global, so
 * counting rows would couple this test to whatever else the suite left
 * behind.
 */
@QuarkusTest
class PairingSweepTest {

    private static final String EXPIRED_CODE = "SWEEPEXP";
    private static final String LIVE_CODE = "SWEEPLIV";

    @Inject
    DeviceControl deviceControl;

    @Inject
    EntityManager entityManager;

    @Inject
    Scheduler scheduler;

    @AfterEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("delete from pairing_request where code in (:expired, :live)")
                .setParameter("expired", EXPIRED_CODE)
                .setParameter("live", LIVE_CODE)
                .executeUpdate());
    }

    private void seed(String code, String expiresAtSql) {
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("insert into pairing_request (code, poll_secret_hash, created_at, expires_at)"
                        + " values (:code, :hash, now() - interval '20 minutes', " + expiresAtSql + ")")
                .setParameter("code", code)
                .setParameter("hash", PairingCodes.sha256Hex("secret-for-" + code))
                .executeUpdate());
    }

    private boolean exists(String code) {
        return ((Number) entityManager
                .createNativeQuery("select count(*) from pairing_request where code = :code")
                .setParameter("code", code).getSingleResult()).intValue() > 0;
    }

    @Test
    void theSweepDeletesExpiredRequestsAndKeepsLiveOnes() {
        seed(EXPIRED_CODE, "now() - interval '1 minute'");
        seed(LIVE_CODE, "now() + interval '10 minutes'");
        assertTrue(exists(EXPIRED_CODE) && exists(LIVE_CODE), "both rows must be seeded before the sweep");

        long deleted = deviceControl.sweepExpiredPairingRequests();

        assertTrue(deleted >= 1, "the sweep reports how many rows it deleted, and it deleted at least ours");
        assertFalse(exists(EXPIRED_CODE), "the expired request must be gone after the sweep");
        assertTrue(exists(LIVE_CODE), "a request inside its TTL must survive the sweep");
    }

    /**
     * Proves the property name in {@code application.properties} is the one
     * this Quarkus version reads: with {@code %test.quarkus.scheduler.start-mode
     * =halted} the scheduler is built but never started, so nothing fires
     * against the shared Dev Services database while the suite runs. A typo
     * in that key would silently fall back to {@code normal} and this is the
     * only place that would notice.
     */
    @Test
    void theSchedulerIsHaltedForTheWholeTestSuite() {
        assertFalse(scheduler.isRunning(),
                "the test profile must run with quarkus.scheduler.start-mode=halted — no scheduled job"
                        + " may fire against the database the whole suite shares");
    }

    @Test
    void theSweepIsIdempotentOnAnAlreadyCleanTable() {
        seed(LIVE_CODE, "now() + interval '10 minutes'");
        deviceControl.sweepExpiredPairingRequests();

        assertEquals(0, deviceControl.sweepExpiredPairingRequests(),
                "a second immediate sweep finds nothing left to delete");
        assertTrue(exists(LIVE_CODE), "and it still does not touch the live request");
    }
}
