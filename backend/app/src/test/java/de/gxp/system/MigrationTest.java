package de.gxp.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Asserts Flyway applied the V1 baseline in the Dev Services database
 * (REQ-205, REQ-215): schema history has a successful V1 entry and the
 * seeded single-row table holds exactly one row.
 */
@QuarkusTest
class MigrationTest {

    @Inject
    EntityManager entityManager;

    @Test
    void flywayAppliedV1Baseline() {
        Number applied = (Number) entityManager
                .createNativeQuery("SELECT count(*) FROM flyway_schema_history"
                        + " WHERE version = '1' AND success = true")
                .getSingleResult();
        assertTrue(applied.longValue() >= 1, "V1 migration not recorded as applied");
    }

    @Test
    void instanceInfoHoldsExactlyTheSeededRow() {
        Number rows = (Number) entityManager
                .createNativeQuery("SELECT count(*) FROM instance_info")
                .getSingleResult();
        assertEquals(1L, rows.longValue(), "instance_info must hold exactly the one seeded row");
    }

    @Test
    void flywayAppliedTheDomainMigrationsV2AndV3() {
        // backend-domain-config (REQ-302): boot reaches V3; V1 stays untouched
        Number applied = (Number) entityManager
                .createNativeQuery("SELECT count(*) FROM flyway_schema_history"
                        + " WHERE version IN ('2', '3') AND success = true")
                .getSingleResult();
        assertEquals(2L, applied.longValue(), "V2 (domain) and V3 (credential) must both be applied");
    }

    @Test
    void flywayHeadIsV9UpdateChannelTrustedKeys() {
        // update-channel (REQ-709, D23/T-20a): V8 + V9 applied, nothing beyond; auth-hardening's V5-V7 below; V4 ships no seeds
        String head = (String) entityManager
                .createNativeQuery("SELECT version FROM flyway_schema_history"
                        + " WHERE success = true ORDER BY installed_rank DESC LIMIT 1")
                .getSingleResult();
        assertEquals("9", head, "Flyway head must be V9 (update channel trusted keys)");
        Number trustedKeyColumns = (Number) entityManager
                .createNativeQuery("SELECT count(*) FROM information_schema.columns"
                        + " WHERE table_name = 'update_channel'"
                        + " AND column_name IN ('trusted_keys', 'trusted_keys_version')")
                .getSingleResult();
        assertEquals(2L, trustedKeyColumns.longValue(), "V9 adds the rotated trust set pair");
        Number totpColumns = (Number) entityManager
                .createNativeQuery("SELECT count(*) FROM information_schema.columns"
                        + " WHERE table_name = 'manager_user'"
                        + " AND column_name IN ('totp_secret', 'totp_confirmed_at', 'totp_last_step')")
                .getSingleResult();
        assertEquals(3L, totpColumns.longValue(), "V6 adds the three TOTP columns");
        Number codeRows = (Number) entityManager
                .createNativeQuery("SELECT count(*) FROM manager_recovery_code")
                .getSingleResult();
        assertEquals(0L, codeRows.longValue(), "a fresh instance has no recovery codes");
        Number devices = (Number) entityManager
                .createNativeQuery("SELECT count(*) FROM device")
                .getSingleResult();
        assertEquals(0L, devices.longValue(), "V4 ships no seeds — a fresh instance has zero devices");
    }

    @Test
    void adminCredentialTableIsGoneAndTheFirstUserIsSeededByCode() {
        // V5 dropped admin_credential (REQ-607) …
        Number oldTable = (Number) entityManager
                .createNativeQuery("SELECT count(*) FROM information_schema.tables"
                        + " WHERE table_name = 'admin_credential'")
                .getSingleResult();
        assertEquals(0L, oldTable.longValue(), "V5 must drop admin_credential");
        // … and ships manager_user WITHOUT a seed row; in the test profile
        // FirstUserSeeder wrote exactly one on startup (REQ-608)
        @SuppressWarnings("unchecked")
        java.util.List<Object[]> rows = entityManager
                .createNativeQuery("SELECT username, password_hash, active FROM manager_user")
                .getResultList();
        assertEquals(1, rows.size(), "the seeder must have written exactly one account");
        assertEquals("admin", rows.get(0)[0], "gxp.admin.username is unset in %test — the default applies");
        assertTrue(String.valueOf(rows.get(0)[1]).startsWith("pbkdf2-sha256$"), "hash, never the plain value");
        assertEquals(Boolean.TRUE, rows.get(0)[2]);
    }

    @Test
    void updateChannelHoldsTheOneSeededRowWithItsDefaults() {
        // update-channel (REQ-709, D8): auto on, 04:00, never checked, nothing available
        Object[] row = (Object[]) entityManager
                .createNativeQuery("SELECT id, auto_update_enabled, apply_at::text, last_check_outcome,"
                        + " available_version, available_manifest FROM update_channel")
                .getSingleResult();
        assertEquals(1, ((Number) row[0]).intValue());
        assertEquals(Boolean.TRUE, row[1]);
        assertEquals("04:00:00", row[2]);
        assertEquals("never-checked", row[3]);
        assertEquals(null, row[4]);
        assertEquals(null, row[5]);
    }
}
