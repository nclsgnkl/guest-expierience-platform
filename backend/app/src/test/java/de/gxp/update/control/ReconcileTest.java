package de.gxp.update.control;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.gxp.api.model.UpdateCheckOutcome;
import de.gxp.update.ManifestServer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** update-channel T-10 (REQ-708, REQ-714, REQ-715, D11–D13): the reconcile algorithm against a planted row, state and clock. */
@QuarkusTest
@QuarkusTestResource(ManifestServer.class)
class ReconcileTest {

    private static final Instant T0 = Instant.parse("2026-09-01T10:00:00Z");
    private static final Path DESIRED = UpdateTestSupport.EXCHANGE.resolve("desired-update.json");

    @Inject
    UpdateControl control;
    @Inject
    UpdateStateStore store;
    @Inject
    EntityManager entityManager;

    @BeforeEach
    @AfterEach
    void reset() throws IOException {
        UpdateTestSupport.reset(entityManager, control);
    }

    /** The fixture's manifest stored as "available", exactly as a successful check would leave it. */
    private void available() throws IOException {
        byte[] manifest = ManifestServer.fixture("manifest.json");
        byte[] signature = Base64.getDecoder().decode(new String(ManifestServer.fixture("manifest.json.sig")).strip());
        store.recordCheck(UpdateCheckOutcome.UPDATE_AVAILABLE, new ManifestParser().parse(manifest), manifest, signature, T0, null);
    }

    private void at(Instant instant) {
        control.useClock(Clock.fixed(instant, ZoneOffset.UTC));
    }

    private ExchangeFiles.DesiredUpdate desired() {
        return new ExchangeFiles(UpdateTestSupport.EXCHANGE).readDesired().orElseThrow();
    }

    @Test
    void anAvailableUpdateIsScheduledOnceAndTheSameWindowKeepsItsRequestedAt() throws IOException {
        available();
        at(T0);
        control.reconcileDesiredUpdate();
        assertArrayEquals(ManifestServer.fixture("desired-update.json"), Files.readAllBytes(DESIRED));
        at(T0.plusSeconds(3600)); // an hour later, same window
        control.reconcileDesiredUpdate();
        assertArrayEquals(ManifestServer.fixture("desired-update.json"), Files.readAllBytes(DESIRED), "requestedAt stays stable");
        at(Instant.parse("2026-09-02T02:30:00Z")); // inside the open window: the updater may still act
        control.reconcileDesiredUpdate();
        assertArrayEquals(ManifestServer.fixture("desired-update.json"), Files.readAllBytes(DESIRED));
    }

    @Test
    void aWindowThatPassedUnappliedMovesToTheNextOccurrenceWithANewRequestedAt() throws IOException {
        available();
        at(T0);
        control.reconcileDesiredUpdate();
        at(Instant.parse("2026-09-02T05:00:00Z")); // 02:00Z + 2 h passed, the box was off
        control.reconcileDesiredUpdate();
        ExchangeFiles.DesiredUpdate moved = desired();
        assertEquals(Instant.parse("2026-09-03T02:00:00Z"), moved.applyNotBefore());
        assertEquals(Instant.parse("2026-09-02T05:00:00Z"), moved.requestedAt(), "a new request for the updater's replay guard");
        assertEquals("auto", moved.requestedBy());
    }

    @Test
    void aChangedApplyTimeMovesAFutureWindow() throws IOException {
        available();
        at(T0);
        control.reconcileDesiredUpdate();
        store.saveSettings(true, LocalTime.of(6, 30), T0);
        control.reconcileDesiredUpdate();
        assertEquals(Instant.parse("2026-09-02T04:30:00Z"), desired().applyNotBefore(), "06:30 Europe/Berlin (CEST)");
    }

    @Test
    void theHoldRuleDropsAVersionTheUpdaterAlreadyAttempted() throws IOException {
        available();
        at(T0);
        control.reconcileDesiredUpdate();
        assertTrue(Files.exists(DESIRED));
        UpdateTestSupport.plantState("state-rolled-back.json"); // attemptedVersion 1.0.1
        control.reconcileDesiredUpdate();
        assertFalse(Files.exists(DESIRED), "D13: one automatic attempt per version");
    }

    @Test
    void disabledAutoUpdateRemovesTheFileUnlessAManualRequestIsPending() throws IOException {
        available();
        at(T0);
        control.reconcileDesiredUpdate();
        store.saveSettings(false, LocalTime.of(4, 0), T0);
        control.reconcileDesiredUpdate();
        assertFalse(Files.exists(DESIRED));
        assertEquals("1.0.1", store.load().availableVersion, "disabled still reports available");

        Instant manualAt = Instant.parse("2026-09-01T12:34:56Z");
        store.recordManualRequest("1.0.1", manualAt);
        control.reconcileDesiredUpdate();
        ExchangeFiles.DesiredUpdate manual = desired();
        assertEquals("manual", manual.requestedBy());
        assertEquals(manualAt, manual.applyNotBefore(), "apply-not-before = now for a manual request");
        assertEquals(manualAt, manual.requestedAt());
    }

    @Test
    void aManualRequestTheUpdaterAlreadyHandledIsNoLongerPendingEvenAfterAFailure() throws IOException {
        available();
        Instant manualAt = Instant.parse("2026-09-01T10:00:00Z"); // == the fixture state's lastRequestedAt
        store.recordManualRequest("1.0.1", manualAt);
        store.saveSettings(false, LocalTime.of(4, 0), T0);
        UpdateTestSupport.plantState("state-rolled-back.json");
        control.reconcileDesiredUpdate();
        assertFalse(Files.exists(DESIRED), "handled (lastRequestedAt == manualRequestedAt) → not pending; auto disabled → no file");

        store.recordManualRequest("1.0.1", manualAt.plusSeconds(60)); // the manager clicks again: a retry is manual only (D13)
        control.reconcileDesiredUpdate();
        assertEquals("manual", desired().requestedBy());
    }

    /** Review R2-1: a manual request recorded at sub-second precision is still "handled" by a state whose lastRequestedAt is its whole second. */
    @Test
    void aSubSecondManualRequestIsHandledByItsWholeSecondCopy() throws IOException {
        available();
        store.recordManualRequest("1.0.1", Instant.parse("2026-09-01T10:00:00.250Z"));
        assertEquals(Instant.parse("2026-09-01T10:00:00Z"), store.load().manualRequestedAt, "stored at second precision");
        store.saveSettings(false, LocalTime.of(4, 0), T0);
        UpdateTestSupport.plantState("state-rolled-back.json"); // lastRequestedAt 2026-09-01T10:00:00Z
        control.reconcileDesiredUpdate();
        assertFalse(Files.exists(DESIRED), "handled, not pending forever");
    }

    /** Review R2-5: a spring-forward gap — 02:30 does not exist on 2027-03-28 in Europe/Berlin; the JDK shifts it forward once (03:30 CEST = 01:30Z). */
    @Test
    void anApplyTimeInsideASpringForwardGapIsShiftedForwardOnce() throws IOException {
        available();
        store.saveSettings(true, LocalTime.of(2, 30), T0);
        at(Instant.parse("2027-03-28T00:10:00Z")); // 01:10 CET, before the gap
        control.reconcileDesiredUpdate();
        assertEquals(Instant.parse("2027-03-28T01:30:00Z"), desired().applyNotBefore());
    }

    /** Review R2-5: a fall-back overlap — at 02:45 CEST (00:45Z) on 2026-10-25 the first 02:30 has passed; the second pass is skipped, the next day's 02:30 CET (01:30Z) is chosen. */
    @Test
    void anApplyTimeInsideAFallBackOverlapDoesNotFireTwice() throws IOException {
        available();
        store.saveSettings(true, LocalTime.of(2, 30), T0);
        at(Instant.parse("2026-10-25T00:45:00Z")); // 02:45 CEST, the first pass of 02:30 is over
        control.reconcileDesiredUpdate();
        assertEquals(Instant.parse("2026-10-26T01:30:00Z"), desired().applyNotBefore());
    }

    @Test
    void nothingAvailableMeansNoFile() throws IOException {
        available();
        at(T0);
        control.reconcileDesiredUpdate();
        store.recordCheck(UpdateCheckOutcome.NO_UPDATE, null, null, null, T0, null);
        control.reconcileDesiredUpdate();
        assertFalse(Files.exists(DESIRED));
    }
}
