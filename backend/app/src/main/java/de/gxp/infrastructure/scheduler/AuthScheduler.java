package de.gxp.infrastructure.scheduler;

import org.jboss.logging.Logger;

import de.gxp.auth.control.AuthControl;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Scheduled housekeeping for {@code de.gxp.auth} (constitution v1.7.0, owner
 * decision 2026-08-25) — the sibling of {@code DeviceScheduler}: one class per
 * domain, holding the trigger and one delegating call, with the logic in the
 * feature's Control.
 *
 * <p>This replaces the purge that used to hang off every token issue, so an
 * instance where nobody logs in for a month no longer keeps a month of dead
 * token hashes in memory. It is hygiene, not an auth control: an expired
 * entry is refused by the store's own expiry check whether or not it has been
 * swept.
 *
 * <p><b>Logging (principle 3).</b> Nothing is logged on success — a count of
 * admin sessions is not information this box needs to write down. A failure
 * logs a fixed message plus the exception's class name, never its message and
 * never a stack trace.
 */
@ApplicationScoped
class AuthScheduler {

    private static final Logger LOG = Logger.getLogger(AuthScheduler.class);

    private final AuthControl authControl;

    AuthScheduler(AuthControl authControl) {
        this.authControl = authControl;
    }

    /**
     * Daily at 02:00 local time, Quartz-style cron (the Quarkus default
     * {@code cron-type}) — see {@code DeviceScheduler} for why an absolute
     * wall-clock schedule is used rather than a relative interval.
     */
    @Scheduled(cron = "0 0 2 * * ?", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void purgeExpiredTokens() {
        try {
            authControl.purgeExpiredTokens();
        } catch (RuntimeException e) {
            LOG.warnf("Admin token housekeeping failed: %s", e.getClass().getName());
        }
    }
}
