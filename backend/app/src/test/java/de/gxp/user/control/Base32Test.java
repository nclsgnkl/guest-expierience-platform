package de.gxp.user.control;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** RFC 4648 §10 test vectors for the hand-written base32 encoder (design D25). */
class Base32Test {

    @ParameterizedTest
    @CsvSource(value = {
            "''|''",
            "f|MY======",
            "fo|MZXQ====",
            "foo|MZXW6===",
            "foob|MZXW6YQ=",
            "fooba|MZXW6YTB",
            "foobar|MZXW6YTBOI======" }, delimiter = '|')
    void rfc4648Vectors(String input, String expected) {
        assertEquals(expected, Base32.encode(input.getBytes(StandardCharsets.US_ASCII)));
    }
}
