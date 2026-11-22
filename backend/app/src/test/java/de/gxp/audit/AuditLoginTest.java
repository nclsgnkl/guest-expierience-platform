package de.gxp.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.gxp.infrastructure.testsupport.ManagerFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * REQ-621 / REQ-624 / REQ-612 (auth-hardening T-12): login outcomes are
 * recorded and commit INDEPENDENTLY of the request — a failed login's row
 * survives the {@code 401}. The attributed address on the LAN is the socket
 * peer ({@code 127.0.0.1}, not via proxy). The throttled {@code 429} case
 * lives in {@code LoginThrottleResourceTest} (it owns the bucket reset), the
 * via-proxy {@code second-factor-required} case in {@code SecondFactorLoginTest}.
 */
@QuarkusTest
class AuditLoginTest {

    private static final String UNKNOWN = "no-such-user-t12";

    @Inject
    EntityManager entityManager;

    private long mark;

    @BeforeEach
    void markRows() {
        mark = maxRowId();
    }

    @AfterEach
    void removeRowsWrittenHere() {
        long from = mark;
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("delete from audit_entry where id > :mark and action = 'login-failure'")
                .setParameter("mark", from).executeUpdate());
    }

    /**
     * REQ-620 (review 2026-08-26): the submitted name of an UNKNOWN user is
     * free text the caller chose — possibly a password typed into the wrong
     * field — and is never stored; the row carries address and outcome only.
     */
    @Test
    void aFailedLoginOfAnUnknownNameLeavesOneRowWithoutTheSubmittedText() {
        ManagerFixtures.attempt(UNKNOWN, "whatever-1").then().statusCode(401);
        // a success clears the shared 127.0.0.1 bucket for the other classes
        ManagerFixtures.login();

        List<Object[]> rows = failureRowsSinceMark();
        assertEquals(1, rows.size(), "exactly one login-failure row although the response was 401");
        Object[] row = rows.get(0);
        assertNull(row[0], "nobody was authenticated — the actor is null (REQ-620)");
        assertEquals("127.0.0.1", row[1]);
        assertEquals(Boolean.FALSE, row[2]);
        assertEquals("login-failure", row[3]);
        assertEquals("user", row[4]);
        assertNull(row[5], "no account matched — the submitted text is never the entity id");
        assertNull(row[6], "no field on a login row");
        for (Object cell : row) {
            assertFalse(UNKNOWN.equals(cell) || "whatever-1".equals(cell), "submitted text in a cell");
        }
    }

    /** REQ-620: a failed login of an EXISTING account records that account's canonical name. */
    @Test
    void aFailedLoginOfAKnownAccountRecordsTheMatchedUsername() {
        ManagerFixtures.attempt(ManagerFixtures.USERNAME, "wrong-password-t12").then().statusCode(401);
        ManagerFixtures.login();

        List<Object[]> rows = failureRowsSinceMark();
        assertEquals(1, rows.size());
        assertNull(rows.get(0)[0], "actor stays null on a failure");
        assertEquals(ManagerFixtures.USERNAME, rows.get(0)[5], "the matched canonical username is the entity id");
    }

    /** REQ-612: the actor of a success row is the login username — the attribution proof. */
    @Test
    void aSuccessfulLoginRecordsTheUsernameAsActor() {
        long before = countSuccessesOf(ManagerFixtures.USERNAME);
        ManagerFixtures.login();
        assertEquals(before + 1, countSuccessesOf(ManagerFixtures.USERNAME));
        Object[] newest = rowsFor(ManagerFixtures.USERNAME).get(0);
        assertEquals(ManagerFixtures.USERNAME, newest[0], "actor = the login username");
        assertEquals("login-success", newest[3]);
        assertEquals("127.0.0.1", newest[1]);
        assertEquals(Boolean.FALSE, newest[2]);
    }

    private long maxRowId() {
        Object max = entityManager.createNativeQuery("select coalesce(max(id), 0) from audit_entry").getSingleResult();
        return ((Number) max).longValue();
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> failureRowsSinceMark() {
        return entityManager.createNativeQuery("select actor, address, via_proxy, action, entity, entity_id, field"
                + " from audit_entry where id > :mark and action = 'login-failure' order by id desc")
                .setParameter("mark", mark).getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rowsFor(String entityId) {
        return entityManager.createNativeQuery("select actor, address, via_proxy, action, entity, entity_id, field"
                + " from audit_entry where entity_id = :id order by id desc")
                .setParameter("id", entityId).getResultList();
    }

    private long countSuccessesOf(String username) {
        return ((Number) entityManager.createNativeQuery("select count(*) from audit_entry"
                + " where action = 'login-success' and actor = :actor")
                .setParameter("actor", username).getSingleResult()).longValue();
    }
}
