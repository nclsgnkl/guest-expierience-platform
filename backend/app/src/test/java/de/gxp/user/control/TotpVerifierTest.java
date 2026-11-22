package de.gxp.user.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.OptionalLong;
import java.util.regex.Pattern;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/**
 * REQ-614: RFC 6238 Appendix B vectors (SHA-1, secret
 * {@code 12345678901234567890}) with a fixed clock, ±1 step drift, ±2
 * refused, replay refused. Plain JUnit — no Quarkus boot.
 */
class TotpVerifierTest {

    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    private static TotpVerifier at(long epochSeconds) {
        return new TotpVerifier(Clock.fixed(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC));
    }

    @ParameterizedTest
    @CsvSource({
            "59, 287082",
            "1111111109, 081804",
            "1111111111, 050471",
            "1234567890, 005924",
            "2000000000, 279037",
            "20000000000, 353130" })
    void rfc6238AppendixBSha1Vectors(long epochSeconds, String expected) {
        TotpVerifier verifier = at(epochSeconds);
        assertEquals(expected, verifier.codeAt(RFC_SECRET, verifier.currentStep()));
        assertEquals(OptionalLong.of(verifier.currentStep()), verifier.verify(RFC_SECRET, expected, null));
    }

    @Test
    void oneStepOfDriftEitherSideIsAcceptedTwoIsNot() {
        TotpVerifier verifier = at(1234567890);
        long now = verifier.currentStep();
        assertEquals(OptionalLong.of(now - 1), verifier.verify(RFC_SECRET, verifier.codeAt(RFC_SECRET, now - 1), null));
        assertEquals(OptionalLong.of(now + 1), verifier.verify(RFC_SECRET, verifier.codeAt(RFC_SECRET, now + 1), null));
        assertTrue(verifier.verify(RFC_SECRET, verifier.codeAt(RFC_SECRET, now - 2), null).isEmpty());
        assertTrue(verifier.verify(RFC_SECRET, verifier.codeAt(RFC_SECRET, now + 2), null).isEmpty());
    }

    @Test
    void aStepAtOrBeforeTheLastAcceptedOneIsAReplay() {
        TotpVerifier verifier = at(1234567890);
        long now = verifier.currentStep();
        String code = verifier.codeAt(RFC_SECRET, now);
        assertEquals(OptionalLong.of(now), verifier.verify(RFC_SECRET, code, null));
        assertTrue(verifier.verify(RFC_SECRET, code, now).isEmpty(), "the same code twice is refused");
        // the previous step's code is also spent once a later step was accepted
        assertTrue(verifier.verify(RFC_SECRET, verifier.codeAt(RFC_SECRET, now - 1), now).isEmpty());
        // the next step is still fresh
        assertEquals(OptionalLong.of(now + 1), verifier.verify(RFC_SECRET, verifier.codeAt(RFC_SECRET, now + 1), now));
    }

    @Test
    void malformedInputIsRefusedWithoutAnException() {
        TotpVerifier verifier = at(1234567890);
        assertTrue(verifier.verify(RFC_SECRET, null, null).isEmpty());
        assertTrue(verifier.verify(RFC_SECRET, "12345", null).isEmpty());
        assertTrue(verifier.verify(RFC_SECRET, "abcdef", null).isEmpty());
        assertTrue(verifier.verify(null, "287082", null).isEmpty());
    }

    @Test
    void secretsAre160BitAndTheUriCarriesThemBase32() {
        TotpVerifier verifier = at(59);
        byte[] secret = verifier.generateSecret();
        assertEquals(20, secret.length);
        assertFalse(java.util.Arrays.equals(secret, verifier.generateSecret()));
        String uri = verifier.otpauthUri("owner", secret);
        assertTrue(Pattern.matches("^otpauth://totp/GXP%3Aowner\\?secret=[A-Z2-7]{32}&issuer=GXP&algorithm=SHA1&digits=6&period=30$", uri), uri);
        assertTrue(uri.contains("secret=" + Base32.encode(secret)));
    }
}
