package de.gxp.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import de.gxp.audit.control.AuditControl;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * REQ-623 (auth-hardening T-14): {@link AuditControl#pruneOlderThan} deletes
 * a 366-day-old row and keeps a 364-day-old one. The scheduler trigger itself
 * is exercised by {@code ScheduledJobExecutionTest}; the suite runs with the
 * scheduler halted, so the Control is called directly.
 */
@QuarkusTest
class AuditRetentionTest {

    private static final String MARKER = "t14-retention";

    @Inject
    AuditControl auditControl;
    @Inject
    EntityManager entityManager;

    @AfterEach
    void removeSeededRows() {
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("delete from audit_entry where action = :action")
                .setParameter("action", MARKER).executeUpdate());
    }

    @Test
    void pruneDeletesA366DayOldRowAndKeepsA364DayOldOne() {
        QuarkusTransaction.requiringNew().run(() -> {
            seed(366, "old");
            seed(364, "young");
        });
        assertEquals(2, seededRows());

        long deleted = auditControl.pruneOlderThan(Duration.ofDays(365));

        assertEquals(1, deleted, "exactly the row beyond the retention is deleted");
        assertEquals(1, seededRows());
        assertEquals("young", entityManager.createNativeQuery("select entity_id from audit_entry where action = :action")
                .setParameter("action", MARKER).getSingleResult());
    }

    private void seed(int ageDays, String entityId) {
        entityManager.createNativeQuery("insert into audit_entry"
                + " (occurred_at, actor, address, via_proxy, action, entity, entity_id, field)"
                + " values (now() - make_interval(days => :age), 'admin', '127.0.0.1', false, :action, 'room', :id, null)")
                .setParameter("age", ageDays).setParameter("action", MARKER).setParameter("id", entityId)
                .executeUpdate();
    }

    private long seededRows() {
        return ((Number) entityManager.createNativeQuery("select count(*) from audit_entry where action = :action")
                .setParameter("action", MARKER).getSingleResult()).longValue();
    }
}
