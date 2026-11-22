package de.gxp.user.control;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Recovery-code generation and at-rest hashing (auth-hardening REQ-616,
 * design D10): a batch of 10 codes, 10 characters {@code [a-z0-9]} each
 * (36^10 ≈ 51.7 bits) from {@link SecureRandom}; stored as SHA-256-hex.
 * High-entropy random secrets get the token treatment, not PBKDF2 — ten
 * derivations per login attempt on the Pi would be a 22-second amplifier.
 * The plain code exists only in the one-time response; nothing here logs.
 */
final class RecoveryCodes {

    static final int BATCH_SIZE = 10;
    static final int CODE_LENGTH = 10;
    private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private RecoveryCodes() {
    }

    /** A fresh batch of plain codes — show once, store {@link #hash}ed. */
    static List<String> generate() {
        List<String> codes = new ArrayList<>(BATCH_SIZE);
        for (int i = 0; i < BATCH_SIZE; i++) {
            StringBuilder code = new StringBuilder(CODE_LENGTH);
            for (int j = 0; j < CODE_LENGTH; j++) {
                code.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
            }
            codes.add(code.toString());
        }
        return codes;
    }

    /** SHA-256 of the code, lower-case hex — the stored form. */
    static String hash(String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
