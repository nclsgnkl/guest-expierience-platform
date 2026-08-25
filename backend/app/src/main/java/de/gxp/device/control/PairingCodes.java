package de.gxp.device.control;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Pairing credential generation (REQ-403/409, pinned parameters). The display
 * code uses a 32-char unambiguous alphabet (Crockford-style — no 0/O/1/I
 * confusion) at 8 chars ≈ 40 bits (32⁸ ≈ 1.1 × 10¹²); combined with the
 * 10-minute TTL that alone makes guessing infeasible — create is not
 * throttled (REQ-409, owner decision 2026-08-24) and the argument no longer
 * leans on a rate limit. The 256-bit poll secret alone protects the token.
 * Logs nothing (principle 3).
 */
final class PairingCodes {

    /** Digits 2-9 + A-Z without O and I — exactly 32 unambiguous chars. */
    static final String CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    static final int CODE_LENGTH = 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PairingCodes() {
    }

    static String newDisplayCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    /** 32 bytes {@link SecureRandom} → base64url, no padding — poll secrets AND device tokens. */
    static String newSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 hex — the only form any secret or token is ever stored in. */
    static String sha256Hex(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
