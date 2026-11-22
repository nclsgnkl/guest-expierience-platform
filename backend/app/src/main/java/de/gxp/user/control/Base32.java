package de.gxp.user.control;

/**
 * RFC 4648 §6 base32 encoder (alphabet {@code A-Z2-7}, {@code =} padding) —
 * the JDK has none and a dependency for 25 lines is not worth it (design
 * D25). Encoder only: the secret is stored base64 (JDK codec) and rendered
 * base32 solely for the {@code otpauth://} URI and the one-time enrollment
 * response, which is what authenticator apps expect. A 20-byte secret
 * encodes to 32 characters without padding.
 */
final class Base32 {

    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private Base32() {
    }

    static String encode(byte[] data) {
        StringBuilder out = new StringBuilder((data.length + 4) / 5 * 8);
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                out.append(ALPHABET[(buffer >> bits) & 0x1f]);
            }
        }
        if (bits > 0) {
            out.append(ALPHABET[(buffer << (5 - bits)) & 0x1f]);
        }
        while (out.length() % 8 != 0) {
            out.append('=');
        }
        return out.toString();
    }
}
