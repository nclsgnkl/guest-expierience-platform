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
    void adminCredentialIsSeededByCodeNotByMigration() {
        // V3 ships the table WITHOUT a seed row; in the test profile the
        // AdminPasswordSeeder wrote exactly one on startup (REQ-330)
        Number rows = (Number) entityManager
                .createNativeQuery("SELECT count(*) FROM admin_credential")
                .getSingleResult();
        assertEquals(1L, rows.longValue(), "the seeder must have written exactly one credential row");
    }
}
