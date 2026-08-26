package de.gxp.user.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.gxp.user.entity.ManagerUser;

/**
 * Component-level tests for {@link FirstUserSeeder} (auth-hardening REQ-608,
 * D15; the eight {@code AdminPasswordSeederTest} cases of REQ-330 ported,
 * plus the username cases) — the seeding decision, exercised with a stubbed
 * repository so no database is needed. The end-to-end proof (test profile
 * seeds, login works) is {@code AuthResourceTest} / {@code FirstRunSeedTest}.
 */
class FirstUserSeederTest {

    /** Records instead of persisting; simulates presence/absence of accounts. */
    private static final class StubRepository extends ManagerUserRepository {
        long existing;
        ManagerUser seeded;

        @Override
        public long count() {
            return existing;
        }

        @Override
        public void persist(ManagerUser user) {
            this.seeded = user;
        }
    }

    private final Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher(1_000);

    private static FirstUserSeeder seeder(StubRepository repository, Pbkdf2PasswordHasher hasher,
            Optional<String> username, Optional<String> password) {
        return new FirstUserSeeder(repository, hasher, username, password);
    }

    @Test
    void unsetPasswordWithNoAccountRefusesStartNamingTheVariableOnly() {
        StubRepository repository = new StubRepository();
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> seeder(repository, hasher, Optional.empty(), Optional.empty()).seedIfMissing());
        assertTrue(failure.getMessage().contains("GXP_ADMIN_PASSWORD"));
        assertNull(repository.seeded);
    }

    @Test
    void blankPasswordWithNoAccountRefusesStart() {
        StubRepository repository = new StubRepository();
        assertThrows(IllegalStateException.class,
                () -> seeder(repository, hasher, Optional.empty(), Optional.of("   ")).seedIfMissing());
        assertNull(repository.seeded);
    }

    @Test
    void tooShortAPasswordWithNoAccountRefusesStartNamingTheRuleOnly() {
        StubRepository repository = new StubRepository();
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> seeder(repository, hasher, Optional.empty(), Optional.of("short7c")).seedIfMissing());
        assertTrue(failure.getMessage().contains("GXP_ADMIN_PASSWORD"));
        assertTrue(failure.getMessage().contains("8"));
        assertFalse(failure.getMessage().contains("short7c"), "the value must never appear in the message");
        assertNull(repository.seeded);
    }

    @Test
    void exactlyTheMinimumLengthIsAccepted() {
        StubRepository repository = new StubRepository();
        seeder(repository, hasher, Optional.empty(), Optional.of("12345678")).seedIfMissing();
        assertTrue(hasher.verify("12345678", repository.seeded.passwordHash));
    }

    @Test
    void anExistingAccountIsNeverRePolicedByTheVariables() {
        // the variables are dead once an account exists — a short leftover
        // value must not turn a running instance into a refused start
        StubRepository repository = new StubRepository();
        repository.existing = 1;
        seeder(repository, hasher, Optional.of("Not Valid!"), Optional.of("a")).seedIfMissing(); // no exception
        assertNull(repository.seeded);
    }

    @Test
    void noAccountSeedsAVerifiableHashNeverThePlainValue() {
        StubRepository repository = new StubRepository();
        seeder(repository, hasher, Optional.empty(), Optional.of("first-start-pw")).seedIfMissing();
        assertTrue(hasher.verify("first-start-pw", repository.seeded.passwordHash));
        assertFalse(repository.seeded.passwordHash.contains("first-start-pw"));
        assertTrue(repository.seeded.active);
        assertEquals(repository.seeded.createdAt, repository.seeded.updatedAt);
    }

    @Test
    void existingAccountIgnoresTheVariablesEntirely() {
        StubRepository repository = new StubRepository();
        repository.existing = 3;
        seeder(repository, hasher, Optional.of("owner"), Optional.of("some-new-value")).seedIfMissing();
        assertNull(repository.seeded);
    }

    @Test
    void existingAccountWithUnsetVariablesStartsFine() {
        StubRepository repository = new StubRepository();
        repository.existing = 1;
        seeder(repository, hasher, Optional.empty(), Optional.empty()).seedIfMissing(); // no exception
        assertNull(repository.seeded);
    }

    @Test
    void theUsernameDefaultsToAdmin() {
        StubRepository repository = new StubRepository();
        seeder(repository, hasher, Optional.empty(), Optional.of("first-start-pw")).seedIfMissing();
        assertEquals("admin", repository.seeded.username);
        // blank counts as unset, too
        StubRepository other = new StubRepository();
        seeder(other, hasher, Optional.of("  "), Optional.of("first-start-pw")).seedIfMissing();
        assertEquals("admin", other.seeded.username);
    }

    @Test
    void gxpAdminUsernameNamesTheFirstAccount() {
        StubRepository repository = new StubRepository();
        seeder(repository, hasher, Optional.of("owner"), Optional.of("first-start-pw")).seedIfMissing();
        assertEquals("owner", repository.seeded.username);
    }

    @Test
    void anInvalidUsernameRefusesStartNamingTheVariableOnly() {
        for (String invalid : new String[] {"Owner", "-owner", "has space", "a".repeat(65), "ümlaut"}) {
            StubRepository repository = new StubRepository();
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> seeder(repository, hasher, Optional.of(invalid), Optional.of("first-start-pw")).seedIfMissing(),
                    invalid);
            assertTrue(failure.getMessage().contains("GXP_ADMIN_USERNAME"));
            assertFalse(failure.getMessage().contains(invalid), "the value must never appear in the message");
            assertNull(repository.seeded);
        }
    }
}
