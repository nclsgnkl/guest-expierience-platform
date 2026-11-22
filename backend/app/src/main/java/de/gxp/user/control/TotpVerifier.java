package de.gxp.user.control;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.OptionalLong;

import jakarta.enterprise.context.ApplicationScoped;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * RFC 6238 TOTP over RFC 4226 HOTP with the JDK's {@link Mac} only
 * (auth-hardening REQ-614, design D9/D25): HMAC-SHA1, 6 digits, 30-second
 * steps, 160-bit {@link SecureRandom} secret — the parameters every
 * authenticator app defaults to. Drift tolerance is ±1 step; the caller
 * passes the user's last accepted step and a code for a step {@code <=} it is
 * refused, which makes every code single-use (replay guard).
 *
 * <p>The clock is injectable (the {@code TokenStore} pattern) so the RFC 6238
 * Appendix B vectors can be pinned in a test. Logs nothing; no secret or
 * code in any message (REQ-619).
 */
@ApplicationScoped
class TotpVerifier {

    static final int STEP_SECONDS = 30;
    static final int DIGITS = 6;
    static final int DRIFT_STEPS = 1;
    static final int SECRET_BYTES = 20;
    static final String ISSUER = "GXP";

    private static final int MODULUS = 1_000_000;

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    TotpVerifier() {
        this(Clock.systemUTC());
    }

    TotpVerifier(Clock clock) {
        this.clock = clock;
    }

    /** A fresh 160-bit shared secret. */
    byte[] generateSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        random.nextBytes(secret);
        return secret;
    }

    /**
     * Accepts {@code code} if it is the code of the current step or one step
     * either side AND that step is later than {@code lastAcceptedStep}
     * ({@code null} = never used). Returns the accepted step — the caller
     * stores it — or empty. Candidates are compared in constant time and all
     * three are always evaluated (no early exit on a match).
     */
    OptionalLong verify(byte[] secret, String code, Long lastAcceptedStep) {
        if (secret == null || code == null || code.length() != DIGITS) {
            return OptionalLong.empty();
        }
        byte[] presented = code.getBytes(StandardCharsets.US_ASCII);
        long now = currentStep();
        long accepted = Long.MIN_VALUE;
        boolean found = false;
        for (long step = now - DRIFT_STEPS; step <= now + DRIFT_STEPS; step++) {
            boolean match = MessageDigest.isEqual(codeAt(secret, step).getBytes(StandardCharsets.US_ASCII), presented);
            boolean fresh = lastAcceptedStep == null || step > lastAcceptedStep;
            if (match && fresh && !found) {
                found = true;
                accepted = step;
            }
        }
        return found ? OptionalLong.of(accepted) : OptionalLong.empty();
    }

    /** The current 30-second step of the injected clock. */
    long currentStep() {
        return Math.floorDiv(clock.instant().getEpochSecond(), STEP_SECONDS);
    }

    /** RFC 4226 HOTP value for that step, zero-padded to six digits. */
    String codeAt(byte[] secret, long step) {
        byte[] hash = hmacSha1(secret, ByteBuffer.allocate(8).putLong(step).array());
        int offset = hash[hash.length - 1] & 0x0f;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
        return String.format("%0" + DIGITS + "d", binary % MODULUS);
    }

    /** Base32 rendering of the secret — what the enrollment response shows once. */
    String base32(byte[] secret) {
        return Base32.encode(secret);
    }

    /** The {@code otpauth://totp/GXP:<username>?...} URI an authenticator app scans. */
    String otpauthUri(String username, byte[] secret) {
        String label = URLEncoder.encode(ISSUER + ":" + username, StandardCharsets.UTF_8);
        return "otpauth://totp/" + label
                + "?secret=" + base32(secret)
                + "&issuer=" + ISSUER
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    private static byte[] hmacSha1(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "RAW"));
            return mac.doFinal(message);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // JDK-guaranteed algorithm; no key material in the message.
            throw new IllegalStateException("HMAC-SHA1 unavailable", e);
        }
    }
}
