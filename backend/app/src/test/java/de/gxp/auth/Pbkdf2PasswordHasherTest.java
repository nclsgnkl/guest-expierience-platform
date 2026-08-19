package de.gxp.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Pbkdf2PasswordHasher} (REQ-329). Plain JUnit — the
 * hasher is a package-private class constructed directly; a low iteration
 * count keeps the test fast, and the self-describing format test proves the
 * stored count (not the instance's) governs verification.
 */
class Pbkdf2PasswordHasherTest {

    private final Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher(1_000);

    @Test
    void roundtripVerifies() {
        String stored = hasher.hash("correct horse battery");
        assertTrue(hasher.verify("correct horse battery", stored));
    }

    @Test
    void wrongPasswordFailsVerification() {
        String stored = hasher.hash("correct horse battery");
        assertFalse(hasher.verify("wrong horse battery", stored));
    }

    @Test
    void storedFormatIsSelfDescribing() {
        String stored = hasher.hash("pw-format-check");
        String[] parts = stored.split("\\$");
        assertEquals(4, parts.length);
        assertEquals("pbkdf2-sha256", parts[0]);
        assertEquals(1_000, Integer.parseInt(parts[1]));
        // salt and key are valid base64 of the pinned sizes (16 B / 256 bit)
        assertEquals(16, java.util.Base64.getDecoder().decode(parts[2]).length);
        assertEquals(32, java.util.Base64.getDecoder().decode(parts[3]).length);
    }

    @Test
    void verificationUsesTheStoredIterationCountNotTheInstanceOne() {
        String stored = new Pbkdf2PasswordHasher(2_000).hash("param-bump");
        // an instance configured differently still verifies the stored value
        assertTrue(new Pbkdf2PasswordHasher(500).verify("param-bump", stored));
    }

    @Test
    void everyHashUsesADistinctSalt() {
        String first = hasher.hash("same password");
        String second = hasher.hash("same password");
        assertNotEquals(first, second);
    }

    @Test
    void malformedStoredValueVerifiesFalseNotThrows() {
        assertFalse(hasher.verify("anything", null));
        assertFalse(hasher.verify("anything", ""));
        assertFalse(hasher.verify("anything", "not-a-hash"));
        assertFalse(hasher.verify("anything", "pbkdf2-sha256$abc$notb64!!$x"));
        assertFalse(hasher.verify("anything", "other-algo$1000$AAAA$BBBB"));
    }
}
