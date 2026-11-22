package de.gxp.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * The TEST signing key pair under {@code src/test/resources/update/} — public
 * by definition, it signs fixtures only (update-channel REQ-725). Never the
 * production key: {@code SigningKeyResourceTest} asserts the two differ.
 */
public final class TestSigningKeys {

    public static final String PRIVATE_RESOURCE = "update/test-signing-private-key.pem";
    public static final String PUBLIC_RESOURCE = "update/test-signing-public-key.pem";
    /** The second (dormant) test key of the two-key set (review A-1). */
    public static final String PRIVATE_RESOURCE_2 = "update/test-signing-private-key-2.pem";
    public static final String PUBLIC_RESOURCE_2 = "update/test-signing-public-key-2.pem";
    /** Both public keys in one PEM — what {@code %test} ships as the trust set. */
    public static final String PUBLIC_SET_RESOURCE = "update/test-signing-public-keys.pem";

    private TestSigningKeys() {
    }

    public static PrivateKey privateKey() {
        return privateKey(PRIVATE_RESOURCE);
    }

    public static PrivateKey privateKey2() {
        return privateKey(PRIVATE_RESOURCE_2);
    }

    public static PublicKey publicKey2() {
        return publicKey(PUBLIC_RESOURCE_2);
    }

    /** The token form of a key (base64 of the SPKI DER) — how a manifest's {@code trustedKeys} names it. */
    public static String token(PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    private static PrivateKey privateKey(String resource) {
        try {
            byte[] der = der(resource, "PRIVATE KEY");
            return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static PublicKey publicKey() {
        return publicKey(PUBLIC_RESOURCE);
    }

    private static PublicKey publicKey(String resource) {
        try {
            byte[] der = der(resource, "PUBLIC KEY");
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Raw 64-byte Ed25519 signature over the exact bytes, with the test private key. */
    public static byte[] sign(byte[] bytes) {
        return sign(bytes, privateKey());
    }

    public static byte[] sign(byte[] bytes, PrivateKey key) {
        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(key);
            signer.update(bytes);
            return signer.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] der(String resource, String label) throws IOException {
        try (InputStream in = TestSigningKeys.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("test resource missing: " + resource);
            }
            String pem = new String(in.readAllBytes(), StandardCharsets.US_ASCII);
            String base64 = pem.replace("-----BEGIN " + label + "-----", "").replace("-----END " + label + "-----", "")
                    .replaceAll("\\s", "");
            return Base64.getDecoder().decode(base64);
        }
    }
}
