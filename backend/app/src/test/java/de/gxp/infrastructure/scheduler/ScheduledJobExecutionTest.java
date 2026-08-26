package de.gxp.infrastructure.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * The scheduled job BODIES actually run here (constitution v1.7.0, owner
 * decision 2026-08-25) — this test exists because nothing else executes them.
 * The suite runs with {@code quarkus.scheduler.start-mode=halted} and
 * {@code PairingSweepTest} calls the Control directly, so without this class
 * the triggers, their delegating calls and their logging gate would first run
 * at 02:00 on a customer's box.
 *
 * <p><b>It lives in the same package as the jobs on purpose:</b>
 * {@code DeviceScheduler}, {@code AuthScheduler} and {@code AuditScheduler} are package-private beans
 * (nothing outside this package may name them), so this is the only place
 * from which they can be injected and invoked.
 *
 * <p><b>How the job is invoked.</b> Each method is called on a fresh thread
 * with a freshly activated request context, which is exactly what
 * {@code io.quarkus.scheduler.common.runtime.DefaultInvoker} does at 02:00
 * (verified against the Quarkus 3.33.3.1 bytecode: it activates the request
 * context when none is active and terminates it afterwards). Calling straight
 * from the test method would instead borrow the request context
 * {@code @QuarkusTest} activates and prove nothing about the scheduler
 * thread. The scheduler is never started and no test waits for a trigger.
 *
 * <p><b>Why the assertions are about LOGS.</b> Both job bodies swallow
 * {@link RuntimeException} by contract, so "it returned" is not evidence that
 * it worked — a broken delegation would return just as quietly. A captured
 * WARN record is therefore the failure signal, and its absence the proof that
 * the call into the feature's Control actually went through.
 *
 * <p><b>Known gap, stated rather than hidden:</b> the {@code catch
 * (RuntimeException)} branch itself is NOT executed here. Reaching it means
 * making a Control throw, which needs either a mocking library (a dependency,
 * and the allow-list is closed) or a test-only failure hook in production
 * code. Neither is worth it for two lines; what IS covered is that the branch
 * is not taken on the happy path, and the guard that keeps the exception's
 * message out of the log is a code review item — see the class javadoc of
 * {@code DeviceScheduler} for what it must never log.
 */
@QuarkusTest
class ScheduledJobExecutionTest {

    private static final String EXPIRED_CODE = "SCHEDEXP";

    /** Any 64-char hex string: the sweep keys on {@code expires_at} only. */
    private static final String POLL_SECRET_HASH =
            "0000000000000000000000000000000000000000000000000000000000000000";

    @Inject
    DeviceScheduler deviceScheduler;

    @Inject
    AuthScheduler authScheduler;

    @Inject
    AuditScheduler auditScheduler;

    @Inject
    EntityManager entityManager;

    @AfterEach
    void cleanUp() {
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("delete from pairing_request where code = :code")
                .setParameter("code", EXPIRED_CODE)
                .executeUpdate());
    }

    @Test
    void theDeviceJobSweepsTheExpiredRowAndLogsTheCountAndNothingElse() {
        seedExpiredPairingRequest();
        assertTrue(exists(EXPIRED_CODE), "the expired row must be seeded before the job runs");

        List<LogRecord> logged = runAsTheSchedulerWould(
                DeviceScheduler.class, deviceScheduler::sweepExpiredPairingRequests);

        assertFalse(exists(EXPIRED_CODE), "the scheduled trigger must reach DeviceControl and delete the row");
        assertTrue(logged.stream().noneMatch(record -> record.getLevel().intValue() >= Level.WARNING.intValue()),
                "the job logged a warning, which means it swallowed an exception instead of doing its"
                        + " work: " + messages(logged));
        assertEquals(1, logged.size(),
                "one INFO line is expected when the sweep deleted something: " + messages(logged));
        assertFalse(messages(logged).contains(EXPIRED_CODE),
                "principle 3: the housekeeping log may carry a COUNT and never a pairing code or hash — "
                        + messages(logged));
    }

    /**
     * The {@code deleted > 0} gate: an instance where nothing expired must
     * write nothing at all, so a year of daily runs leaves no daily noise.
     * The table is drained by a first run rather than by a {@code delete
     * from}, so this test destroys no state another class seeded.
     */
    @Test
    void theDeviceJobIsSilentWhenThereIsNothingToDelete() {
        runAsTheSchedulerWould(DeviceScheduler.class, deviceScheduler::sweepExpiredPairingRequests);

        List<LogRecord> logged = runAsTheSchedulerWould(
                DeviceScheduler.class, deviceScheduler::sweepExpiredPairingRequests);

        assertTrue(logged.isEmpty(),
                "a sweep that deleted nothing must log nothing — the count gate is what keeps an idle"
                        + " instance's log empty: " + messages(logged));
    }

    /**
     * The auth job has no count to report, so success is silent by design;
     * the assertion that matters is that nothing was logged AT ALL, since the
     * only thing this job can produce is the WARN of a failed delegation.
     */
    @Test
    void theAuthJobPurgesThroughTheControlAndStaysSilent() {
        List<LogRecord> logged = runAsTheSchedulerWould(
                AuthScheduler.class, authScheduler::purgeExpiredTokens);

        assertTrue(logged.isEmpty(),
                "the token purge must complete silently; anything logged here is the failure branch: "
                        + messages(logged));
    }

    /**
     * The audit job (auth-hardening T-14, REQ-623) runs through the Control
     * and, with nothing older than 365 days, is silent — the count gate. The
     * retention arithmetic itself is {@code de.gxp.audit.AuditRetentionTest}.
     */
    @Test
    void theAuditJobPrunesThroughTheControlAndIsSilentWhenNothingExpired() {
        List<LogRecord> logged = runAsTheSchedulerWould(
                AuditScheduler.class, auditScheduler::pruneExpiredEntries);

        assertTrue(logged.isEmpty(),
                "the audit prune must be silent when nothing is older than the retention; anything logged"
                        + " here is the failure branch: " + messages(logged));
    }

    /**
     * Runs {@code job} the way the scheduler will: on another thread, inside
     * a request context activated for that run, with everything the category
     * logged captured and returned. Rethrows whatever escaped the job — the
     * bodies are supposed to let nothing escape.
     */
    private List<LogRecord> runAsTheSchedulerWould(Class<?> category, Runnable job) {
        List<LogRecord> records = Collections.synchronizedList(new ArrayList<>());
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        java.util.logging.Logger categoryLogger = java.util.logging.Logger.getLogger(category.getName());
        categoryLogger.addHandler(capture);
        AtomicReference<Throwable> escaped = new AtomicReference<>();
        try {
            Thread thread = new Thread(() -> {
                ManagedContext requestContext = Arc.container().requestContext();
                requestContext.activate();
                try {
                    job.run();
                } finally {
                    requestContext.terminate();
                }
            }, "scheduled-job-under-test");
            thread.setUncaughtExceptionHandler((t, e) -> escaped.set(e));
            thread.start();
            join(thread);
        } finally {
            categoryLogger.removeHandler(capture);
        }
        assertNull(escaped.get(),
                "a scheduled job must not let anything escape — Quarkus would log an uncaught exception"
                        + " with a full stack trace (principle 3): " + escaped.get());
        return List.copyOf(records);
    }

    private static void join(Thread thread) {
        try {
            thread.join(30_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the scheduled job", e);
        }
        assertFalse(thread.isAlive(), "the scheduled job did not finish within 30 s");
    }

    private static String messages(List<LogRecord> records) {
        return records.stream().map(LogRecord::getMessage).reduce((a, b) -> a + " | " + b).orElse("(nothing)");
    }

    private void seedExpiredPairingRequest() {
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("insert into pairing_request (code, poll_secret_hash, created_at, expires_at)"
                        + " values (:code, :hash, now() - interval '20 minutes', now() - interval '1 minute')")
                .setParameter("code", EXPIRED_CODE)
                .setParameter("hash", POLL_SECRET_HASH)
                .executeUpdate());
    }

    private boolean exists(String code) {
        return ((Number) entityManager
                .createNativeQuery("select count(*) from pairing_request where code = :code")
                .setParameter("code", code).getSingleResult()).intValue() > 0;
    }
}
