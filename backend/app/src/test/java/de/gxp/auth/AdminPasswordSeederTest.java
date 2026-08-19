package de.gxp.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Component-level tests for {@link AdminPasswordSeeder} (REQ-330) — the
 * seeding decision, exercised with a stubbed repository so no database is
 * needed. The end-to-end proof (test profile seeds, login works) is
 * {@code AuthResourceTest}.
 */
class AdminPasswordSeederTest {

    /** Records instead of persisting; simulates presence/absence of the row. */
    private static final class StubRepository extends AdminCredentialRepository {
        AdminCredential existing;
        String seededHash;

        @Override
        AdminCredential theRow() {
            return existing;
        }

        @Override
        void seed(String passwordHash, Instant now) {
            this.seededHash = passwordHash;
        }
    }

    private final Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher(1_000);

    @Test
    void unsetVariableWithNoRowRefusesStartNamingTheVariableOnly() {
        StubRepository repository = new StubRepository();
        AdminPasswordSeeder seeder = new AdminPasswordSeeder(repository, hasher, Optional.empty());
        IllegalStateException failure = assertThrows(IllegalStateException.class, seeder::seedIfMissing);
        assertTrue(failure.getMessage().contains("GXP_ADMIN_PASSWORD"));
        assertNull(repository.seededHash);
    }

    @Test
    void blankVariableWithNoRowRefusesStart() {
        StubRepository repository = new StubRepository();
        AdminPasswordSeeder seeder = new AdminPasswordSeeder(repository, hasher, Optional.of("   "));
        assertThrows(IllegalStateException.class, seeder::seedIfMissing);
        assertNull(repository.seededHash);
    }

    @Test
    void noRowSeedsAVerifiableHashNeverThePlainValue() {
        StubRepository repository = new StubRepository();
        AdminPasswordSeeder seeder = new AdminPasswordSeeder(repository, hasher, Optional.of("first-start-pw"));
        seeder.seedIfMissing();
        assertTrue(hasher.verify("first-start-pw", repository.seededHash));
        assertFalse(repository.seededHash.contains("first-start-pw"));
    }

    @Test
    void existingRowIgnoresTheVariableEntirely() {
        StubRepository repository = new StubRepository();
        repository.existing = new AdminCredential();
        repository.existing.passwordHash = "pbkdf2-sha256$1000$AAAA$BBBB";
        AdminPasswordSeeder seeder = new AdminPasswordSeeder(repository, hasher, Optional.of("some-new-value"));
        seeder.seedIfMissing();
        assertNull(repository.seededHash);
        assertEquals("pbkdf2-sha256$1000$AAAA$BBBB", repository.existing.passwordHash);
    }

    @Test
    void existingRowWithUnsetVariableStartsFine() {
        StubRepository repository = new StubRepository();
        repository.existing = new AdminCredential();
        AdminPasswordSeeder seeder = new AdminPasswordSeeder(repository, hasher, Optional.empty());
        seeder.seedIfMissing(); // no exception
        assertNull(repository.seededHash);
    }
}
