package de.gxp.infrastructure.scheduler;

import java.time.Duration;

import org.jboss.logging.Logger;

import de.gxp.audit.control.AuditControl;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Scheduled housekeeping for {@code de.gxp.audit} (auth-hardening REQ-623,
 * owner decision 2026-08-25 §12 item 5): the audit trail keeps 365 days and
 * this daily trigger is the ONLY deletion that exists — there is no delete
 * endpoint. Same shape as {@code DeviceScheduler} / {@code AuthScheduler}:
 * one class per domain, the annotation and one delegating call, the logic in
 * the feature's Control.
 *
 * <p><b>Logging (principle 3).</b> Success logs a row COUNT and only when
 * something was deleted; a failure logs a fixed message plus the exception's
 * class name, never its message (a {@code PSQLException} quotes statement
 * text) and never a stack trace.
 */
@ApplicationScoped
class AuditScheduler {

    private static final Logger LOG = Logger.getLogger(AuditScheduler.class);

    /** 365 days (§12 item 5) — no size cap. */
    static final Duration RETENTION = Duration.ofDays(365);

    private final AuditControl auditControl;

    AuditScheduler(AuditControl auditControl) {
        this.auditControl = auditControl;
    }

    /**
     * Daily at 02:00 local time, Quartz-style cron — see {@code DeviceScheduler}
     * for why an absolute wall-clock schedule is used rather than a relative
     * interval. {@code SKIP}: an overrunning run is skipped, never overlapped.
     */
    @Scheduled(cron = "0 0 2 * * ?", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void pruneExpiredEntries() {
        try {
            long deleted = auditControl.pruneOlderThan(RETENTION);
            if (deleted > 0) {
                LOG.infof("Audit housekeeping deleted %d entries older than %d days", deleted, RETENTION.toDays());
            }
        } catch (RuntimeException e) {
            LOG.warnf("Audit housekeeping failed: %s", e.getClass().getName());
        }
    }
}
