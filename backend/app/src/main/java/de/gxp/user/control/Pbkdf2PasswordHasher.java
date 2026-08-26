package de.gxp.user.control;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * JDK PBKDF2 password hashing (REQ-329, constitution: no Elytron). Pinned
 * parameters: PBKDF2WithHmacSHA256, 600 000 iterations (OWASP 2023), 16-byte
 * random salt, 256-bit key. Stored self-describing as
 * {@code pbkdf2-sha256$<iterations>$<salt-b64>$<hash-b64>}, so verification
 * always uses the STORED iteration count, so a later parameter bump keeps
 * every existing hash verifiable (there is no rehash-on-login; a bump
 * applies to newly stored hashes only).
 *
 * <p>The iteration count is configurable only so the test profile can keep
 * the suite fast on small hardware; the production default is the pinned
 * 600 000.
 *
 * <p>This class logs nothing and never puts password material into an
 * exception message (constitution principle 3).
 */
@ApplicationScoped
class Pbkdf2PasswordHasher {

    static final String PREFIX = "pbkdf2-sha256";
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private final int iterations;
    private final SecureRandom random = new SecureRandom();
    /** Test probe: how often {@link #verify} derived a key (proves the throttle rejects BEFORE PBKDF2). */
    private final AtomicLong verifications = new AtomicLong();

    Pbkdf2PasswordHasher(
            @ConfigProperty(name = "gxp.auth.pbkdf2-iterations", defaultValue = "600000") int iterations) {
        this.iterations = iterations;
    }

    /** Hashes with a fresh random salt; every call yields a distinct value. */
    String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] key = derive(password, salt, iterations);
        Base64.Encoder b64 = Base64.getEncoder();
        return PREFIX + "$" + iterations + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(key);
    }

    /**
     * Verifies against the self-describing stored format using a
     * constant-time comparison. A malformed stored value verifies as
     * {@code false} — never as an exception that could leak detail.
     */
    boolean verify(String password, String stored) {
        verifications.incrementAndGet();
        if (stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            int storedIterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(password, salt, storedIterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Package-private test probe — the number of {@link #verify} calls so far. */
    long verifyCount() {
        return verifications.get();
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            // JDK-guaranteed algorithm; no password material in the message.
            throw new IllegalStateException("PBKDF2 derivation unavailable", e);
        } finally {
            spec.clearPassword();
        }
    }
}
