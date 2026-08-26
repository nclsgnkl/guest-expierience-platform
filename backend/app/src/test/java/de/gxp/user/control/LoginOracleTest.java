package de.gxp.user.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import de.gxp.infrastructure.testsupport.ManagerFixtures;
import de.gxp.user.entity.ManagerUser;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * REQ-609's same-work half: an unknown username and a deactivated account
 * each cost exactly one PBKDF2 verification, the same as a wrong password
 * for a known active user — so neither
 * wording nor timing tells them apart. Lives in {@code de.gxp.user.control}
 * because {@link Pbkdf2PasswordHasher#verifyCount()} is package-private.
 * (The byte-identical-body half is in {@code de.gxp.auth.AuthResourceTest}.)
 */
@QuarkusTest
class LoginOracleTest {

    @Inject
    UserControl userControl;
    @Inject
    Pbkdf2PasswordHasher hasher;
    @Inject
    ManagerUserRepository users;

    @Test
    void unknownUserAndWrongPasswordCostOneDerivationEach() {
        long before = hasher.verifyCount();
        UserControl.Verdict unknown = userControl.verifyLogin("nobody-here", "definitely-wrong");
        assertEquals(before + 1, hasher.verifyCount(), "the dummy hash must be verified for an unknown user");
        assertFalse(unknown.ok());
        assertNull(unknown.username());

        UserControl.Verdict wrong = userControl.verifyLogin(ManagerFixtures.USERNAME, "definitely-wrong");
        assertEquals(before + 2, hasher.verifyCount());
        assertFalse(wrong.ok());
        assertNull(wrong.username());
        // what the client can see is identical; matchedUsername is audit-internal (REQ-620) and never surfaces
        assertEquals(unknown.ok(), wrong.ok());
        assertEquals(unknown.username(), wrong.username());
        assertEquals(ManagerFixtures.USERNAME, wrong.matchedUsername());
        assertNull(unknown.matchedUsername());

        UserControl.Verdict ok = userControl.verifyLogin(ManagerFixtures.USERNAME, ManagerFixtures.PASSWORD);
        assertEquals(before + 3, hasher.verifyCount());
        assertTrue(ok.ok());
        assertEquals(ManagerFixtures.USERNAME, ok.username());
    }

    /** The third of REQ-609's three: a deactivated account costs the same one derivation. */
    @Test
    void aDeactivatedUserCostsOneDerivationToo() {
        String username = "deactivated-oracle";
        String password = "deactivated-password-1";
        QuarkusTransaction.requiringNew().run(() -> {
            ManagerUser user = new ManagerUser();
            user.username = username;
            user.passwordHash = hasher.hash(password);
            user.active = false;
            user.createdAt = Instant.now();
            user.updatedAt = user.createdAt;
            users.persist(user);
        });
        try {
            long before = hasher.verifyCount();
            UserControl.Verdict inactive = userControl.verifyLogin(username, password);
            assertEquals(before + 1, hasher.verifyCount(), "the dummy hash must be verified for a deactivated user");
            assertFalse(inactive.ok());
            assertNull(inactive.username());
            assertEquals(username, inactive.matchedUsername(), "the audit names the matched account, active or not");
        } finally {
            QuarkusTransaction.requiringNew().run(() -> users.delete("username", username));
        }
    }
}
