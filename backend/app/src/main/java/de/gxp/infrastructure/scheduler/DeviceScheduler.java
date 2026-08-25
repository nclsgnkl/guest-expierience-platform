package de.gxp.infrastructure.scheduler;

import org.jboss.logging.Logger;

import de.gxp.device.control.DeviceControl;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Scheduled housekeeping for {@code de.gxp.device} (constitution v1.7.0,
 * owner decision 2026-08-25). Scheduling is centrally managed here — one
 * class per domain — so that "what runs on a timer" is answerable from one
 * directory; the logic itself stays in the feature's Control, and this class
 * holds nothing but the trigger and one delegating call.
 *
 * <p><b>Never on a Boundary.</b> A {@code @Scheduled} method on a
 * {@code *Resource} would be security-checked by
 * {@code quarkus.security.deny-unannotated-members} (class-scoped) with no
 * identity present and denied at runtime, while every test stayed green.
 *
 * <p><b>Logging (principle 3).</b> An uncaught failure would be logged by
 * Quarkus with a full stack trace, and a {@code PSQLException} can quote
 * statement text and constraint values — so the {@link RuntimeException} is
 * caught here and only its CLASS NAME is logged, never its message. Success
 * logs a row COUNT and only when there was something to report.
 */
@ApplicationScoped
class DeviceScheduler {

    private static final Logger LOG = Logger.getLogger(DeviceScheduler.class);

    private final DeviceControl deviceControl;

    DeviceScheduler(DeviceControl deviceControl) {
        this.deviceControl = deviceControl;
    }

    /**
     * Daily at 02:00 local time, Quartz-style cron (the Quarkus default
     * {@code cron-type}). Deliberately a cron and not {@code every = "24h"}:
     * a relative interval restarts with the process, and an on-premise box
     * reboots whenever the building's power does, so a rebooting instance
     * could keep postponing its housekeeping forever. {@code SKIP} means a
     * run that is still going when the next one is due is skipped rather
     * than overlapped.
     */
    @Scheduled(cron = "0 0 2 * * ?", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void sweepExpiredPairingRequests() {
        try {
            long deleted = deviceControl.sweepExpiredPairingRequests();
            if (deleted > 0) {
                LOG.infof("Pairing housekeeping deleted %d expired pairing request(s)", deleted);
            }
        } catch (RuntimeException e) {
            LOG.warnf("Pairing housekeeping failed: %s", e.getClass().getName());
        }
    }
}
