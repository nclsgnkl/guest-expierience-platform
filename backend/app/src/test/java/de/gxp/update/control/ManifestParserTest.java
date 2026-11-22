package de.gxp.update.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import de.gxp.api.model.UpdateCheckOutcome;

/** update-channel T-08 (REQ-702, REQ-705, REQ-727): strict parse + every acceptance rule. Plain JUnit. */
class ManifestParserTest {

    private static final String SHA = "0123456789abcdef".repeat(4);
    private static final SemanticVersion RUNNING = SemanticVersion.parse("1.0.0-SNAPSHOT");
    private final ManifestParser parser = new ManifestParser();

    /** The fixed layout sign-manifest.sh writes (8 lines, 9 with minimumFromVersion). */
    private static byte[] manifest(String channel, String version, String minimum, int schemaVersion) {
        String tail = minimum == null
                ? "  \"publishedAt\": \"2026-09-01T10:00:00Z\"\n"
                : "  \"publishedAt\": \"2026-09-01T10:00:00Z\",\n  \"minimumFromVersion\": \"" + minimum + "\"\n";
        return ("{\n  \"schemaVersion\": " + schemaVersion + ",\n  \"channel\": \"" + channel + "\",\n  \"version\": \""
                + version + "\",\n  \"artifactRef\": \"ghcr.io/example/gxp-backend:" + version + "\",\n  \"sha256\": \""
                + SHA + "\",\n" + tail + "}\n").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] good() {
        return manifest("stable", "1.0.1", null, 1);
    }

    @Test
    void aRegistryOnAPortIsAValidArtifactRef() {
        // T-20c: the rehearsal registry (and any registry on a non-default port) is `host:port/name:tag`
        assertTrue(ManifestParser.ARTIFACT_REF.matcher("localhost:5000/gxp-backend:9.9.9").matches());
        assertTrue(ManifestParser.ARTIFACT_REF.matcher("registry.example.com:5000/gxp/backend:1.0.1@sha256:" + SHA).matches());
        assertFalse(ManifestParser.ARTIFACT_REF.matcher("ghcr.io//gxp-backend:1.0.1").matches());
        assertFalse(ManifestParser.ARTIFACT_REF.matcher("ghcr.io:abc/gxp-backend:1.0.1").matches());
    }

    @Test
    void aGoodManifestParsesAndIsAccepted() {
        Manifest m = parser.parse(good());
        assertEquals(1, m.schemaVersion());
        assertEquals("stable", m.channel());
        assertEquals("1.0.1", m.version());
        assertEquals("ghcr.io/example/gxp-backend:1.0.1", m.artifactRef());
        assertEquals(SHA, m.sha256());
        assertEquals(Instant.parse("2026-09-01T10:00:00Z"), m.publishedAt());
        assertNull(m.minimumFromVersion());
        assertNull(parser.accept(m, "stable", RUNNING));
        Manifest withMinimum = parser.parse(manifest("stable", "1.0.1", "1.0.0-SNAPSHOT", 1));
        assertEquals("1.0.0-SNAPSHOT", withMinimum.minimumFromVersion());
        assertNull(parser.accept(withMinimum, "stable", RUNNING), "minimumFromVersion == running is met");
    }

    @Test
    void anotherChannelIsWrongChannel() {
        assertEquals(UpdateCheckOutcome.WRONG_CHANNEL, parser.accept(parser.parse(manifest("beta", "1.0.1", null, 1)), "stable", RUNNING));
    }

    @Test
    void schemaVersionTwoIsWrongSchemaVersionEvenWhenEverythingElseFits() {
        assertEquals(UpdateCheckOutcome.WRONG_SCHEMA_VERSION, parser.accept(parser.parse(manifest("stable", "1.0.1", null, 2)), "stable", RUNNING));
    }

    @Test
    void anEqualOrLowerVersionIsNoUpdate() {
        // REQ-727: a replayed, validly signed older manifest does nothing
        assertEquals(UpdateCheckOutcome.NO_UPDATE, parser.accept(parser.parse(manifest("stable", "1.0.0-SNAPSHOT", null, 1)), "stable", RUNNING));
        assertEquals(UpdateCheckOutcome.NO_UPDATE, parser.accept(parser.parse(manifest("stable", "0.9.9", null, 1)), "stable", RUNNING));
        assertEquals(UpdateCheckOutcome.NO_UPDATE, parser.accept(parser.parse(manifest("stable", "1.0.1", null, 1)), "stable", SemanticVersion.parse("1.0.1")));
        assertNull(parser.accept(parser.parse(manifest("stable", "1.0.0", null, 1)), "stable", RUNNING), "the 1.0.0 release is above 1.0.0-SNAPSHOT");
    }

    @Test
    void aMinimumFromVersionAboveTheRunningOneIsNotMet() {
        assertEquals(UpdateCheckOutcome.MINIMUM_FROM_VERSION_NOT_MET,
                parser.accept(parser.parse(manifest("stable", "2.0.0", "1.0.0", 1)), "stable", RUNNING));
    }

    @Test
    void anUnknownFieldIsMalformed() {
        String withExtra = new String(good(), StandardCharsets.UTF_8).replace("  \"sha256\"", "  \"extra\": 1,\n  \"sha256\"");
        assertThrows(IllegalArgumentException.class, () -> parser.parse(withExtra.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void aVersionCarryingAShellCommandIsMalformedAndNeverEchoed() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(manifest("stable", "1.1.0; rm -rf /", null, 1)));
        assertFalse(e.getMessage().contains("rm -rf"));
    }

    @Test
    void everyOtherFieldIsRegexValidatedAndRequired() {
        String base = new String(good(), StandardCharsets.UTF_8);
        for (String broken : new String[] {
                base.replace("ghcr.io/example/gxp-backend:1.0.1", "Ghcr.io/x"),                  // upper-case ref
                base.replace("ghcr.io/example/gxp-backend:1.0.1", "ref; rm -rf /"),
                base.replace(SHA, SHA.substring(1)),                                             // 63 hex
                base.replace("2026-09-01T10:00:00Z", "2026-09-01T10:00:00+02:00"),               // not Z
                base.replace("2026-09-01T10:00:00Z", "2026-02-30T00:00:00Z"),                    // regex-valid, impossible (#3)
                base.replace("\"1.0.1\"", "\"99999999999.0.0\""),                                  // regex-valid, overflows (#4)
                base.replace("  \"sha256\"", "  \"channel\": \"stable\",\n  \"sha256\""),          // duplicate key (#13)
                base.replace("\"stable\"", "\"Stable\""),
                base.replace("  \"channel\": \"stable\",\n", ""),                                 // missing field
                base.replace("\"schemaVersion\": 1", "\"schemaVersion\": \"1\""),                // wrong JSON type
                "", "not json", "[]", "null" }) {
            assertThrows(IllegalArgumentException.class, () -> parser.parse(broken.getBytes(StandardCharsets.UTF_8)), broken);
        }
        assertThrows(IllegalArgumentException.class, () -> parser.parse(new byte[64 * 1024 + 1]), "above 64 KiB");
        assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
        // #4: an overflowing minimumFromVersion is rejected in parse, so accept can never throw
        assertThrows(IllegalArgumentException.class, () -> parser.parse(manifest("stable", "2.0.0", "99999999999.0.0", 1)));
    }

    @Test
    void trustedKeysIsOptionalAndEveryTokenMustBeAnEd25519PublicKey() {
        String base = new String(good(), StandardCharsets.UTF_8);
        String one = de.gxp.update.TestSigningKeys.token(de.gxp.update.TestSigningKeys.publicKey());
        String two = de.gxp.update.TestSigningKeys.token(de.gxp.update.TestSigningKeys.publicKey2());
        String withSet = base.replace("  \"publishedAt\": \"2026-09-01T10:00:00Z\"\n",
                "  \"publishedAt\": \"2026-09-01T10:00:00Z\",\n  \"trustedKeys\": \"" + one + "," + two + "\"\n");
        Manifest m = parser.parse(withSet.getBytes(StandardCharsets.UTF_8));
        assertEquals(one + "," + two, m.trustedKeys());
        assertNull(parser.accept(m, "stable", RUNNING));
        assertNull(parser.parse(good()).trustedKeys(), "absent = no rotation");
        for (String broken : new String[] {
                withSet.replace(one + "," + two, ""),                                       // empty
                withSet.replace(one + "," + two, one + "," + one),                          // duplicated key
                withSet.replace(one + "," + two, "A".repeat(59) + "="),                     // shape-valid, not a key
                withSet.replace(one + "," + two, one.substring(1)),                         // 59 chars
                withSet.replace(one + "," + two, String.join(",", one, two, one, two, one)), // five
                withSet.replace("\"" + one + "," + two + "\"", "[\"" + one + "\"]") }) {   // an array is not the layout
            assertThrows(IllegalArgumentException.class, () -> parser.parse(broken.getBytes(StandardCharsets.UTF_8)), broken);
        }
    }
}
