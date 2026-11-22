package de.gxp.update.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** update-channel T-05 (REQ-705, REQ-727, D6) — plain JUnit. */
class SemanticVersionTest {

    private static int compare(String a, String b) {
        return SemanticVersion.parse(a).compareTo(SemanticVersion.parse(b));
    }

    @Test
    void ordersReleasesNumericallyAndAQualifierBelowItsRelease() {
        List<String> ascending = List.of("1.0.0-SNAPSHOT", "1.0.0", "1.0.1", "1.1.0", "2.0.0", "10.0.0");
        for (int i = 0; i < ascending.size() - 1; i++) {
            assertTrue(compare(ascending.get(i), ascending.get(i + 1)) < 0, ascending.get(i) + " < " + ascending.get(i + 1));
            assertTrue(compare(ascending.get(i + 1), ascending.get(i)) > 0);
        }
        assertEquals(0, compare("1.2.3", "1.2.3"));
        assertTrue(compare("1.0.10", "1.0.9") > 0, "numeric, not lexical");
    }

    @Test
    void twoQualifiersCompareLexically() {
        assertTrue(compare("1.0.0-SNAPSHOT", "1.0.0-rc1") < 0, "'S' < 'r' in ASCII");
        assertTrue(compare("1.0.0-rc2", "1.0.0-rc1") > 0);
        assertEquals(0, compare("1.0.0-rc1", "1.0.0-rc1"));
    }

    @Test
    void garbageIsRejectedWithoutEchoingIt() {
        for (String bad : List.of("", "1", "1.0", "v1.0.0", "1.0.0.0", "1.1.0; rm -rf /", "1.0.0-", "1.0.0-a b",
                "01.0.0-" + "x".repeat(33), "99999999999.0.0")) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse(bad), bad);
            assertFalse(e.getMessage().contains("rm"), "the message must not carry the input");
        }
        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse(null));
    }

    @Test
    void roundTripsThroughToString() {
        assertEquals("1.0.0-SNAPSHOT", SemanticVersion.parse("1.0.0-SNAPSHOT").toString());
        assertEquals("2.10.3", SemanticVersion.parse("2.10.3").toString());
    }
}
