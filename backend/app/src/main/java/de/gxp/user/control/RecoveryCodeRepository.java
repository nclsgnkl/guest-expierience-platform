package de.gxp.user.control;

import java.time.Instant;
import java.util.List;

import de.gxp.user.entity.ManagerUser;
import de.gxp.user.entity.RecoveryCode;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Control (data access) — package-private data access for recovery codes
 * (auth-hardening REQ-616). Queries are by user and never by the plain code:
 * the caller hashes first and matches against the unused rows.
 */
@ApplicationScoped
class RecoveryCodeRepository implements PanacheRepository<RecoveryCode> {

    /** The unspent codes of that user. */
    List<RecoveryCode> unusedOf(ManagerUser user) {
        return list("user = ?1 and usedAt is null", user);
    }

    long countUnused(ManagerUser user) {
        return count("user = ?1 and usedAt is null", user);
    }

    /**
     * Spends the row ATOMICALLY (review 2026-08-26 #1, REQ-616 "exactly
     * once"): a conditional UPDATE whose predicate re-checks {@code usedAt}
     * under the row lock, so of two concurrent logins presenting the same
     * code exactly one sees an affected row. Returns {@code false} when the
     * row was already spent by the time the UPDATE ran.
     */
    boolean markUsed(RecoveryCode row, Instant now) {
        return update("usedAt = ?1 where id = ?2 and usedAt is null", now, row.id) == 1;
    }

    /** Drops every code of that user, spent or not (regenerate, disable, admin reset). */
    long deleteAllOf(ManagerUser user) {
        return delete("user", user);
    }
}
