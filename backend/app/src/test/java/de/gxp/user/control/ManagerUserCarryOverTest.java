package de.gxp.user.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Proves the one thing an upgrade must get right (REQ-607, design "How the
 * carry-over is proven"): V5's {@code INSERT … SELECT} carries the legacy
 * {@code admin_credential} row over as the active user {@code admin} with
 * the IDENTICAL hash. A fresh test database has no legacy row, so the
 * pre-V5 shape is recreated as a TEMP table inside a rolled-back transaction
 * and the statement is read VERBATIM from the migration file — the test
 * cannot drift from the SQL that ships.
 */
@QuarkusTest
class ManagerUserCarryOverTest {

    private static final String LEGACY_HASH = "pbkdf2-sha256$600000$bGVnYWN5LXNhbHQ=$bGVnYWN5LWhhc2g=";

    @Inject
    EntityManager entityManager;

    @Test
    @TestTransaction
    void theLegacyCredentialRowBecomesTheActiveUserAdminWithTheSameHash() throws IOException {
        entityManager.createNativeQuery(
                "CREATE TEMP TABLE admin_credential (id smallint PRIMARY KEY CHECK (id = 1),"
                        + " password_hash text NOT NULL, updated_at timestamptz NOT NULL) ON COMMIT DROP")
                .executeUpdate();
        entityManager.createNativeQuery(
                "INSERT INTO admin_credential (id, password_hash, updated_at)"
                        + " VALUES (1, :hash, '2026-01-02T03:04:05Z')")
                .setParameter("hash", LEGACY_HASH)
                .executeUpdate();
        // the seeded test account is in the way of the UNIQUE username — this
        // transaction is rolled back, so removing it here is invisible
        entityManager.createNativeQuery("DELETE FROM manager_user").executeUpdate();

        int carried = entityManager.createNativeQuery(carryOverStatementFromV5()).executeUpdate();
        assertEquals(1, carried);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager
                .createNativeQuery("SELECT username, password_hash, active, created_at = updated_at FROM manager_user")
                .getResultList();
        assertEquals(1, rows.size());
        assertEquals("admin", rows.get(0)[0]);
        assertEquals(LEGACY_HASH, rows.get(0)[1], "the hash must survive the upgrade byte for byte");
        assertEquals(Boolean.TRUE, rows.get(0)[2]);
        assertEquals(Boolean.TRUE, rows.get(0)[3], "created_at is taken from the legacy updated_at");
    }

    /** The {@code INSERT … SELECT} of V5, read from the file that ships. */
    private static String carryOverStatementFromV5() throws IOException {
        Path migration = Path.of("src/main/resources/db/migration/V5__manager_user.sql");
        String sql = Files.readString(migration);
        Matcher statement = Pattern.compile("INSERT INTO manager_user[^;]+;").matcher(sql);
        assertTrue(statement.find(), "V5 must carry admin_credential over with an INSERT INTO manager_user");
        return statement.group().replaceAll(";$", "");
    }
}
