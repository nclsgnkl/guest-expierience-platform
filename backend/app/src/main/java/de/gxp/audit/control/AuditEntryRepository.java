package de.gxp.audit.control;

import java.time.Instant;
import java.util.List;

import de.gxp.audit.entity.AuditEntry;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Control (data access) — package-private data access for the audit trail
 * (auth-hardening REQ-620/622/623). Insert, page newest-first, and the one
 * deletion that exists: retention. No update path at all.
 */
@ApplicationScoped
class AuditEntryRepository implements PanacheRepository<AuditEntry> {

    /** Newest first: {@code ORDER BY occurred_at DESC, id DESC} (the V7 index). */
    List<AuditEntry> newestFirst(int page, int size) {
        return findAll(Sort.descending("occurredAt", "id")).page(Page.of(page, size)).list();
    }

    /** Retention (REQ-623): rows that occurred strictly before the cut-off. */
    long deleteOlderThan(Instant cutOff) {
        return delete("occurredAt < ?1", cutOff);
    }
}
