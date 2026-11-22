package de.gxp.update.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.PublicKey;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.gxp.update.TestSigningKeys;

/**
 * The loader fails closed (update-channel REQ-704): a missing or unparseable
 * resource is an {@code IllegalStateException} that names only the resource
 * — thrown from the {@code StartupEvent} observer, which is what makes
 * Quarkus refuse to boot. Asserted directly on the observer method (the
 * design's fallback): a {@code @QuarkusTest} that expects its own boot to
 * fail cannot report the failure as a green assertion under Dev Services.
 * Review A-1: the resource is a SET (one PEM, several blocks) and keys have a
 * canonical token form.
 */
class SigningKeyLoaderTest {

    @Test
    void loadsTheTwoTestKeysFromOneResourceInOrder() {
        SigningKeyLoader loader = new SigningKeyLoader(TestSigningKeys.PUBLIC_SET_RESOURCE);
        loader.onStart(null);
        List<PublicKey> keys = loader.keys();
        assertEquals(2, keys.size());
        assertEquals("EdDSA", keys.get(0).getAlgorithm());
        assertTrue(SigningKeyLoader.same(TestSigningKeys.publicKey(), keys.get(0)));
        assertTrue(SigningKeyLoader.same(TestSigningKeys.publicKey2(), keys.get(1)));
        assertFalse(SigningKeyLoader.same(keys.get(0), keys.get(1)));
    }

    @Test
    void aSingleKeyResourceIsASetOfOne() {
        SigningKeyLoader loader = new SigningKeyLoader(TestSigningKeys.PUBLIC_RESOURCE);
        assertEquals(1, loader.keys().size());
    }

    @Test
    void tokensRoundTripAndRejectGarbage() {
        List<PublicKey> set = List.of(TestSigningKeys.publicKey(), TestSigningKeys.publicKey2());
        String tokens = SigningKeyLoader.tokens(set);
        assertTrue(SigningKeyLoader.TOKEN_SET.matcher(tokens).matches(), tokens);
        assertEquals(TestSigningKeys.token(TestSigningKeys.publicKey()) + "," + TestSigningKeys.token(TestSigningKeys.publicKey2()), tokens);
        List<PublicKey> back = SigningKeyLoader.parseTokens(tokens);
        assertEquals(2, back.size());
        assertTrue(SigningKeyLoader.contains(back, TestSigningKeys.publicKey2()));
        assertThrows(IllegalArgumentException.class, () -> SigningKeyLoader.parseTokens(""));
        assertThrows(IllegalArgumentException.class, () -> SigningKeyLoader.parseTokens(tokens + "," + tokens), "five tokens / duplicates");
        String one = TestSigningKeys.token(TestSigningKeys.publicKey());
        assertThrows(IllegalArgumentException.class, () -> SigningKeyLoader.parseTokens(one + "," + one), "duplicated key");
        // shape-valid base64 that is not an Ed25519 SubjectPublicKeyInfo
        assertThrows(IllegalArgumentException.class, () -> SigningKeyLoader.parseTokens("A".repeat(59) + "="));
    }

    @Test
    void aMissingResourceRefusesStartupNamingOnlyThePath() {
        SigningKeyLoader loader = new SigningKeyLoader("update/does-not-exist.pem");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> loader.onStart(null));
        assertEquals("update signing public key missing or invalid: update/does-not-exist.pem", e.getMessage());
        assertThrows(IllegalStateException.class, loader::keys);
    }

    @Test
    void aResourceThatIsNotAPublicKeyRefusesStartup() {
        // the PRIVATE test key is a PEM file, but not a PUBLIC KEY block — refused, and its bytes never echoed
        SigningKeyLoader loader = new SigningKeyLoader(TestSigningKeys.PRIVATE_RESOURCE);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> loader.onStart(null));
        assertTrue(e.getMessage().endsWith(TestSigningKeys.PRIVATE_RESOURCE));
        assertFalse(e.getMessage().contains("M"), "no base64 material in the message");
    }
}
