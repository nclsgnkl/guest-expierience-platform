package de.gxp.infrastructure.scheduler;

import org.jboss.logging.Logger;

import de.gxp.update.control.UpdateControl;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Scheduled work for {@code de.gxp.update} (update-channel REQ-707/708,
 * D10/D11): the hourly manifest check and the 15-minute reconciliation of
 * the desired-update file. Same shape as the other schedulers — one class
 * per domain, the triggers and one delegating call each, the logic in
 * {@link UpdateControl}.
 *
 * <p><b>Logging (principle 3).</b> Nothing on success — the Control logs an
 * outcome CODE when a check fails; a thrown failure here logs a fixed
 * message plus the exception's class name, never its message and never a
 * stack trace (nothing on this path may name a URL or a manifest byte).
 */
@ApplicationScoped
class UpdateScheduler {

    private static final Logger LOG = Logger.getLogger(UpdateScheduler.class);

    private final UpdateControl updateControl;

    UpdateScheduler(UpdateControl updateControl) {
        this.updateControl = updateControl;
    }

    /**
     * Hourly at minute 17 (Quartz cron; an off-the-hour minute so a fleet
     * does not line up with every other hourly job on the internet — the
     * Control adds 0–600 s of jitter on top). {@code SKIP}: a check still
     * sleeping its jitter or waiting on the server is never overlapped.
     */
    @Scheduled(cron = "0 17 * * * ?", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void checkForUpdate() {
        try {
            updateControl.checkForUpdate();
        } catch (RuntimeException e) {
            LOG.warnf("Update check job failed: %s", e.getClass().getName());
        }
    }

    /** Every 15 minutes: moves a passed apply window forward, applies the hold rule, removes a stale file. */
    @Scheduled(cron = "0 */15 * * * ?", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void reconcileDesiredUpdate() {
        try {
            updateControl.reconcileDesiredUpdate();
        } catch (RuntimeException e) {
            LOG.warnf("Update reconcile job failed: %s", e.getClass().getName());
        }
    }
}
