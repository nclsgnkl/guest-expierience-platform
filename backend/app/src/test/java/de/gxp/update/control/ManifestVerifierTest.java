package de.gxp.update.control;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.gxp.update.TestSigningKeys;

/** update-channel T-06 (REQ-702, REQ-703, D4; review A-1): detached Ed25519 over exact bytes against a trust SET, JDK only. */
class ManifestVerifierTest {

    private static final byte[] MANIFEST = "{\n  \"schemaVersion\": 1,\n  \"channel\": \"stable\"\n}\n"
            .getBytes(StandardCharsets.UTF_8);

    private final ManifestVerifier verifier = new ManifestVerifier();
    private final List<PublicKey> set = new SigningKeyLoader(TestSigningKeys.PUBLIC_SET_RESOURCE).keys();

    @Test
    void eitherKeyOfTheSetVerifiesAndTheSignerIsReturned() {
        PublicKey byOne = verifier.verify(MANIFEST, TestSigningKeys.sign(MANIFEST), set);
        assertTrue(byOne != null && SigningKeyLoader.same(byOne, TestSigningKeys.publicKey()), "key 1 (active)");
        PublicKey byTwo = verifier.verify(MANIFEST, TestSigningKeys.sign(MANIFEST, TestSigningKeys.privateKey2()), set);
        assertTrue(byTwo != null && SigningKeyLoader.same(byTwo, TestSigningKeys.publicKey2()), "key 2 (dormant)");
    }

    @Test
    void aKeyOutsideTheSetIsRefusedEvenIfItWasTrustedBefore() {
        List<PublicKey> rotated = List.of(TestSigningKeys.publicKey2());
        assertNull(verifier.verify(MANIFEST, TestSigningKeys.sign(MANIFEST), rotated), "key 1 retired");
        assertNull(verifier.verify(MANIFEST, TestSigningKeys.sign(MANIFEST), List.of()), "empty set trusts nothing");
    }

    @Test
    void oneFlippedByteAnywhereFails() {
        byte[] signature = TestSigningKeys.sign(MANIFEST);
        for (int i = 0; i < MANIFEST.length; i++) {
            byte[] tampered = MANIFEST.clone();
            tampered[i] ^= 0x01;
            assertNull(verifier.verify(tampered, signature, set), "byte " + i + " (whitespace included)");
        }
        byte[] tamperedSignature = signature.clone();
        tamperedSignature[10] ^= 0x01;
        assertNull(verifier.verify(MANIFEST, tamperedSignature, set));
    }

    @Test
    void aSignatureByAnotherKeyFails() throws Exception {
        KeyPair other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        assertNull(verifier.verify(MANIFEST, TestSigningKeys.sign(MANIFEST, other.getPrivate()), set));
    }

    @Test
    void aSignatureOfTheWrongLengthOrNullFailsWithoutThrowing() {
        byte[] signature = TestSigningKeys.sign(MANIFEST);
        assertNull(verifier.verify(MANIFEST, Arrays.copyOf(signature, 63), set));
        assertNull(verifier.verify(MANIFEST, Arrays.copyOf(signature, 65), set));
        assertNull(verifier.verify(MANIFEST, new byte[0], set));
        assertNull(verifier.verify(MANIFEST, null, set));
        assertNull(verifier.verify(null, signature, set));
        assertNull(verifier.verify(MANIFEST, signature, null));
    }
}
